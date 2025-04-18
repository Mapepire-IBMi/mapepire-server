package com.github.ibm.mapepire.authfile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.ibm.mapepire.MapepireServer;
import com.github.ibm.mapepire.Tracer;
import com.github.ibm.mapepire.authfile.AuthRule.AuthCheckResult;
import com.github.ibm.mapepire.authfile.AuthRule.RuleType;
import com.github.theprez.jcmdutils.ProcessLauncher;
import com.github.theprez.jcmdutils.ProcessLauncher.ProcessResult;

public class AuthFile {

    private static final String DEFAULT_SEC_FILE = "/QOpenSys/etc/mapepire/iprules.conf";
    private static final String DEFAULT_SEC_FILE_SINGLEMODE = "/QOpenSys/etc/mapepire/iprules-single.conf";
    private static AuthFile s_defaultInstance = null;

    public synchronized static AuthFile getDefault() {
        if (null != s_defaultInstance) {
            return s_defaultInstance;
        }
        return new AuthFile(MapepireServer.isSingleMode()? DEFAULT_SEC_FILE_SINGLEMODE:DEFAULT_SEC_FILE);
    }
    public synchronized static void disableDefaultAuthFile() { 
        s_defaultInstance = new AuthFile("/dev/null");
    }

    private final File m_file;
    private final Pattern s_authRulePattern = Pattern.compile("^\\s*(deny|allow)\\s+([*\\w]+)\\s*@\\s*([0-9*:.]+)\\s*$", Pattern.CASE_INSENSITIVE);

    private List<AuthRule> m_rules;

    public AuthFile(final File _f) {
        m_file = _f;
    }

    public AuthFile(final String _file) {
        this(new File(_file));
    }

    public synchronized List<AuthRule> getRules() throws IOException {
        if (null != m_rules) {
            return m_rules;
        }
        if(!m_file.isFile()) {
            Tracer.info("IP security rules file not found. Disabling IP security. File location: "+m_file.getAbsolutePath());
            return m_rules = Collections.emptyList();
        }
        if(!m_file.canRead()) {
            Tracer.err("IP security rules file not readable. Disabling IP security. File location: "+m_file.getAbsolutePath());
            throw new FileNotFoundException(m_file.getAbsolutePath());
        }
        if(m_file.canWrite()) {
            Tracer.warn("WARNING: IP security rules file is writable: "+m_file.getAbsolutePath());
            ProcessResult chmodResult = ProcessLauncher.exec("/QOpenSys/usr/bin/chmod o-w "+m_file.getAbsolutePath());
            Tracer.info("Exit code from chmod command: "+chmodResult.getExitStatus());
        }
        final List<AuthRule> ret = new LinkedList<AuthRule>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(m_file), "UTF-8"))) {
            String line = null;
            int lineNumber = 0;
            while (null != (line = br.readLine())) {
                lineNumber++;
                final AuthRule rule = parseAuthRuleFromLine(lineNumber, line);
                if (null != rule) {
                    ret.add(rule);
                }
            }
        }
        return m_rules = ret;
    }

    private AuthRule parseAuthRuleFromLine(final int _lineNumber, final String _line) throws IOException {
        final String line = _line.trim();

        if (line.startsWith("//") || line.startsWith("#") || line.isEmpty()) {
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
        return new AuthRule(_lineNumber, ruleType, user, ip);
    }

    public void verify(final String _user, final String _ip) throws IOException {

        AuthRule lastMatchingRule = null;
        for (final AuthRule rule : getRules()) {
            final AuthCheckResult checkResult = rule.check(_user, _ip);
            if (checkResult.isMatch()) {
                lastMatchingRule = rule;
            }
        }
        if (null == lastMatchingRule) {
            Tracer.info(String.format("Connection for %s@%s has no matching governance rule", _user, _ip));
            return;
        }
        if (null != lastMatchingRule && RuleType.DENY == lastMatchingRule.getRuleType()) {
            throw new IOException("Connection refused by security rule at line " + lastMatchingRule.getLineNumber());
        }
        Tracer.info(String.format("Connection for %s@%s allowed by security rule at line %d", _user, _ip, lastMatchingRule.getLineNumber()));
    }
}
