package com.github.ibm.mapepire.authfile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.ibm.mapepire.MapepireServer;
import com.github.ibm.mapepire.SystemConnection;
import com.github.ibm.mapepire.Tracer;
import com.github.ibm.mapepire.authfile.AuthRule.AuthCheckResult;
import com.github.ibm.mapepire.authfile.AuthRule.RuleType;
import com.github.theprez.jcmdutils.ProcessLauncher;
import com.github.theprez.jcmdutils.ProcessLauncher.ProcessResult;

/**
 * Loads and evaluates the IP/user connection rules, read from the rules file and, on IBM i, the
 * QAIE.IPRULES governance table.
 */
public class AuthFile {

    private static final String DEFAULT_SEC_FILE = "/QOpenSys/etc/mapepire/iprules.conf";
    private static final String DEFAULT_SEC_FILE_SINGLEMODE = "/QOpenSys/etc/mapepire/iprules-single.conf";
    private static final long GOVERNANCE_QUERY_TIMEOUT_SECONDS = 60;
    private static AuthFile s_defaultInstance = null;
    private static final Pattern s_authRulePattern = Pattern.compile("^\\s*(deny|allow|allowbasiconly)\\s+([*\\w]+)\\s*@\\s*([0-9*:.]+)\\s*$", Pattern.CASE_INSENSITIVE);
    
    public static synchronized AuthFile getDefault() {
        if (null != s_defaultInstance) {
            return s_defaultInstance;
        }
        return s_defaultInstance = new AuthFile(MapepireServer.isSingleMode() ? DEFAULT_SEC_FILE_SINGLEMODE : DEFAULT_SEC_FILE);
    }

    public static synchronized void disableDefaultAuthFile() {
        s_defaultInstance = new AuthFile("/dev/null");
    }

    private final File m_file;

    private List<AuthRule> m_rules;

    public AuthFile(final File _file) {
        m_file = _file;
    }

    public AuthFile(final String _file) {
        this(new File(_file));
    }

    public synchronized List<AuthRule> getRules() throws IOException {
        if (null != m_rules) {
            return m_rules;
        }
        final List<AuthRule> ret = new LinkedList<AuthRule>();
        if (m_file.isFile()) {
            if (!m_file.canRead()) {
                Tracer.globalErr("IP security rules file not readable. Disabling IP security. File location: " + m_file.getAbsolutePath());
                throw new FileNotFoundException(m_file.getAbsolutePath());
            }
            if (m_file.canWrite()) {
                Tracer.globalWarn("WARNING: IP security rules file is writable: " + m_file.getAbsolutePath());
                final ProcessResult chmodResult =ProcessLauncher.exec("/QOpenSys/usr/bin/chmod o-w " + m_file.getAbsolutePath());
                Tracer.globalInfo("Exit code from chmod command: " + chmodResult.getExitStatus());
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(m_file), "UTF-8"))) {
                String line = null;
                int lineNumber = 0;
                while (null != (line = br.readLine())) {
                    lineNumber++;
                    final AuthRule rule = parseAuthRuleFromLine(false, lineNumber, line);
                    if (null != rule) {
                        ret.add(rule);
                    }
                }
            }
        } else {
            Tracer.globalInfo("IP security rules file not found: " + m_file.getAbsolutePath());
        }
        if (SystemConnection.isRunningOnIBMi() && new File("/qsys.lib/qaie.lib/iprules.file").exists()) {
            // This check is intentionally brutal. If this fails, Mapepire startup will fail, for security reasons
            Tracer.globalInfo("Attempting to load IP rules from governance table.");
            final Process p =Runtime.getRuntime().exec(new String[] { "/usr/bin/qsh", "-c", "/usr/bin/db2 -s \"select RULE, FILTER from QAIE.IPRULES order by priority desc\"" });
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), "Cp037"))) {
                String line = null;
                int lineNumber = 0;
                boolean isSkippingHeaderLines = true;
                while (null != (line = br.readLine())) {
                    lineNumber++;
                    if (isSkippingHeaderLines) {
                        if (line.trim().startsWith("--")) {
                            isSkippingHeaderLines = false;
                            continue;
                        } else {
                            continue;
                        }
                    } else {
                        if (line.trim().isEmpty()) {
                            break;
                        }
                    }
                    Tracer.globalInfo("Processing rule from governance table: " + line);
                    final AuthRule rule = parseAuthRuleFromLine(true, lineNumber, line);
                    if (null != rule) {
                        ret.add(rule);
                    }
                }
                if (isSkippingHeaderLines) {
                    // we got to the end without encountering a line starting with '--'.
                    // That means we are processing output in a different CCSID than qsh is returning
                    throw new IOException("Error processing contents of governance table");
                }
            }
            // Bounded wait: stderr is never drained, so an unbounded wait could hang startup indefinitely
            final boolean isExited;
            try {
                isExited = p.waitFor(GOVERNANCE_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (final InterruptedException _e) {
                Thread.currentThread().interrupt();
                p.destroyForcibly();
                throw new IOException("Interrupted while processing governance table", _e);
            }
            if (!isExited) {
                p.destroyForcibly();
                throw new IOException("Timed out processing governance table");
            }
            if (0 != p.exitValue()) {
                throw new IOException("Error processing governance table");
            }
        }
        return m_rules = ret;
    }

    private AuthRule parseAuthRuleFromLine(final boolean _isFromTable, final int _lineNumber, final String _line) throws IOException {
        final String line = _line.trim();

        if (line.startsWith("//") || line.startsWith("#") || line.startsWith("--") || line.isEmpty()) {
            // comment or empty line, ignore it
            return null;
        }
        final Matcher m = s_authRulePattern.matcher(line);
        if (!m.matches()) {
            throw new IOException("Invalid entry in authorization configuration at line " + _lineNumber);
        }
        final RuleType ruleType = RuleType.valueOf(m.group(1).toUpperCase());
        final String user = m.group(2);
        final String ip = m.group(3);
        return new AuthRule(_isFromTable, _lineNumber, ruleType, user, ip);
    }

    /**
     * Find the rule that governs a connection, refusing the connection if that rule is a deny. Rules are
     * evaluated in order and the last matching rule wins; if no rule matches, the connection is allowed.
     *
     * @param _user
     *            the user profile requesting the connection
     * @param _ip
     *            the client IP address the connection originated from
     * @return the last matching rule, or a synthesized allow rule if none matched
     * @throws IOException
     *             if the connection is refused by a security rule, or the rules cannot be loaded
     */
    public AuthRule getAccessRuleAndThrowIfDeny(final String _user, final String _ip) throws IOException {
        AuthRule lastMatchingRule = null;
        for (final AuthRule rule : getRules()) {
            final AuthCheckResult checkResult = rule.check(_user, _ip);
            if (checkResult.isMatch()) {
                lastMatchingRule = rule;
            }
        }
        if (null == lastMatchingRule) {
            Tracer.globalInfo(String.format("Connection for %s@%s has no matching governance rule", _user, _ip));
            return new AuthRule(false, -1, RuleType.ALLOW, _user, _ip);
        }

        if (null != lastMatchingRule && RuleType.DENY == lastMatchingRule.getRuleType()) {
            throw new IOException("Connection refused by security rule from " + lastMatchingRule.getLocationString());
        }

        Tracer.globalInfo(String.format("Connection for %s@%s allowed by security rule (%s)", _user, _ip, lastMatchingRule.getLocationString()));

        return lastMatchingRule;
    }
}
