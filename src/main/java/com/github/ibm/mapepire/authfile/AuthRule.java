package com.github.ibm.mapepire.authfile;

import java.net.InetAddress;
import java.util.regex.Pattern;

public class AuthRule {
    public enum RuleType {
        ALLOW, DENY
    }

    public class AuthCheckResult {

        private final boolean m_isMatch;

        public AuthCheckResult(final boolean _isMatch) {
            m_isMatch = _isMatch;
        }

        public RuleType geRuleType() {
            return m_ruleType;
        }

        public boolean isMatch() {
            return m_isMatch;
        }

    }

    private final RuleType m_ruleType;

    protected final String m_user;
    protected String m_ip;

    private final Pattern m_userPattern;

    private final Pattern m_ipPattern;

    private final int m_lineNumber;

    public AuthRule(final int _lineNumber, final RuleType _type, final String _user, final String _ip) {
        m_ruleType = _type;
        m_user = _user;
        m_ip = _ip;
        m_lineNumber = _lineNumber;
        m_userPattern = Pattern.compile(getRegexFromSplattedLiteral(_user), Pattern.CASE_INSENSITIVE);
        m_ipPattern = Pattern.compile(getRegexFromSplattedLiteral(_ip), Pattern.CASE_INSENSITIVE);
    }

    public AuthCheckResult check(final String _user, final String _addr) {
        final boolean userMatches = m_userPattern.matcher(_user).matches();
        final boolean ipMatches = m_ipPattern.matcher(_addr).matches();
        final boolean matches = userMatches && ipMatches;
        return new AuthCheckResult(matches);
    }

    public RuleType getRuleType() {
        return m_ruleType;
    }

    private static String getRegexFromSplattedLiteral(final String _s) {
        String re = "";
        final String splatRegex = "[0-9:.$#A-Z]*";
        final Pattern charsNotNeedingQuoting = Pattern.compile("[\\w:]", Pattern.CASE_INSENSITIVE);
        for (final char l : _s.toCharArray()) {
            final String s = String.valueOf(l);
            if (l == '*') {
                re += splatRegex;
            } else if (l == '.') {
                re += "\\.";
            } else if (charsNotNeedingQuoting.matcher(s).matches()) {
                re += s;
            } else {
                re += Pattern.quote(s);
            }
        }
        return "^" + re + "$";
    }

    public int getLineNumber() {
        return m_lineNumber;
    }

}
