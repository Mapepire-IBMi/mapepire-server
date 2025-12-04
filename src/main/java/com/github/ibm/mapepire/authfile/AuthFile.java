package com.github.ibm.mapepire.authfile;

import java.io.*;
import java.util.*;
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
    private static final String DEFAULT_SEC_DIR = DEFAULT_SEC_FILE + ".d";
    private static final String DEFAULT_SEC_FILE_SINGLEMODE = "/QOpenSys/etc/mapepire/iprules-single.conf";
    private static final String DEFAULT_SEC_DIR_SINGLEMODE = DEFAULT_SEC_FILE_SINGLEMODE + ".d";
    private static AuthFile s_defaultInstance = null;

    public synchronized static AuthFile getDefault() {
        if (null != s_defaultInstance) {
            return s_defaultInstance;
        }
        return new AuthFile(MapepireServer.isSingleMode() ? DEFAULT_SEC_FILE_SINGLEMODE : DEFAULT_SEC_FILE,
                MapepireServer.isSingleMode() ? DEFAULT_SEC_DIR_SINGLEMODE : DEFAULT_SEC_DIR);
    }

    public synchronized static void disableDefaultAuthFile() {
        s_defaultInstance = new AuthFile("/dev/null", "/dev/null");
    }

    private final File m_file;
    private final File m_dir;
    private final Pattern s_authRulePattern = Pattern.compile("^\\s*(deny|allow)\\s+([*\\w]+)\\s*@\\s*([0-9*:.]+)\\s*$", Pattern.CASE_INSENSITIVE);

    private List<AuthRule> m_rules;

    public AuthFile(final File _f, final File _d) {
        m_file = _f;
        m_dir = _d;
    }

    public AuthFile(final String _file, final String _dir) {
        this(new File(_file), new File(_dir));
    }

    public synchronized List<AuthRule> getRules() throws IOException {
        if (null != m_rules) {
            return m_rules;
        }
        final List<AuthRule> ret = new LinkedList<AuthRule>();
        // Load the legacy IP security rules file
        loadRules(m_file, ret);

        // Load the rules from directory
        if (!m_dir.isDirectory()) {
            Tracer.info("IP security rules directory not found. Directory location: " + m_dir.getAbsolutePath());
        } else {
            if (!m_dir.canExecute()) {
                Tracer.info("IP security rules directory is not executable. Directory location: " + m_dir.getAbsolutePath());
            } else {
                if (m_dir.canWrite()) {
                    Tracer.warn("WARNING: IP security rules directory is writable: " + m_dir.getAbsolutePath());
                    ProcessResult chmodResult = ProcessLauncher.exec("/QOpenSys/usr/bin/chmod o-w " + m_dir.getAbsolutePath());
                    Tracer.info("Exit code from chmod command: " + chmodResult.getExitStatus());
                }
                List<File> toSort = new ArrayList<>();
                Collections.addAll(toSort, m_dir.listFiles((File dir, String name) -> name.endsWith(".conf")));
                toSort.sort(Comparator.comparing(File::getName));
                for (File file : toSort) {
                    loadRules(file, ret);
                }
            }
        }
        m_rules = ret;
        return ret;
    }

    private void loadRules(final File _f, List<AuthRule> ret) throws IOException {
        Tracer.info("Loading rules from file: " + _f.getAbsolutePath());
        if (!_f.isFile()) {
            Tracer.info("IP security rules file not found. File location: " + _f.getAbsolutePath());
            return;
        }
        if (!_f.canRead()) {
            Tracer.err("IP security rules file not readable. File location: " + _f.getAbsolutePath());
            throw new FileNotFoundException(_f.getAbsolutePath());
        }
        if (_f.canWrite()) {
            Tracer.warn("WARNING: IP security rules file is writable: " + _f.getAbsolutePath());
            ProcessResult chmodResult = ProcessLauncher.exec("/QOpenSys/usr/bin/chmod o-w " + _f.getAbsolutePath());
            Tracer.info("Exit code from chmod command: " + chmodResult.getExitStatus());
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(_f), "UTF-8"))) {
            String line = null;
            int lineNumber = 0;
            while (null != (line = br.readLine())) {
                lineNumber++;
                final AuthRule rule = parseAuthRuleFromLine(lineNumber, _f.getAbsolutePath(), line);
                if (null != rule) {
                    ret.add(rule);
                }
            }
        }
    }

    private AuthRule parseAuthRuleFromLine(final int _lineNumber, final String _fileName, final String _line) throws IOException {
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
        return new AuthRule(_lineNumber, _fileName, ruleType, user, ip);
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
            throw new IOException("Connection refused by security rule at line " + lastMatchingRule.getLineNumber() + " of " + lastMatchingRule.getFileName());
        }
        Tracer.info(String.format("Connection for %s@%s allowed by security rule at line %d", _user, _ip, lastMatchingRule.getLineNumber()));
    }
}
