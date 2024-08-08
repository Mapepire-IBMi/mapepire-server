package com.github.ibm.mapepire;

import java.util.Properties;

import com.github.theprez.jcmdutils.StringUtils;

public class ClientSpecialRegisters {

    private static final String CLIENT_APP_NAME = "ApplicationName"; // AS400JDBCConnectionImpl.applicationNamePropertyName_
    private static final String CLIENT_USER = "ClientUser"; // AS400JDBCConnectionImpl.clientUserPropertyName_
    private static final String CLIENT_HOST_NAME = "ClientHostname"; // AS400JDBCConnectionImpl.clientHostnamePropertyName_
    private static final String CLIENT_ACCOUNTING = "ClientAccounting"; // AS400JDBCConnectionImpl.clientAccountingPropertyName_
    private static final String CLIENT_PGM_ID = "ClientProgramID"; // AS400JDBCConnectionImpl.clientProgramIDPropertyName_
    final String m_clientIP;
    final String m_accountingString;
    private String m_applicationName = "I dunno, maybe VSCode or something";

    public ClientSpecialRegisters() {
        String sshConnectionEnv = System.getenv("SSH_CONNECTION");
        if (StringUtils.isNonEmpty(sshConnectionEnv)) {
            m_clientIP = sshConnectionEnv.replaceAll("\\s.*", "");
        } else {
            sshConnectionEnv = System.getenv("SSH_CLIENT");
            if (StringUtils.isNonEmpty(sshConnectionEnv)) {
                m_clientIP = sshConnectionEnv.replaceAll("\\s.*", "");
            } else {
                m_clientIP = "localhost";
            }
        }
        String location = "<unknown location>";
        try {
            location = ClientSpecialRegisters.class.getProtectionDomain().getCodeSource().getLocation().toString();
        } catch (Exception e) {
            Tracer.err(e);
        }
        m_accountingString = "location: "+location;
    }

    public ClientSpecialRegisters setApplicationName(String _appName) {
        m_applicationName = _appName;
        return this;
    }

    public Properties getProperties() {
        Properties ret = new Properties();
        ret.put(CLIENT_USER, System.getProperty("user.name", "<unknown>"));
        ret.put(CLIENT_APP_NAME, m_applicationName);
        ret.put(CLIENT_HOST_NAME, m_clientIP);
        ret.put(CLIENT_PGM_ID, getProgramString());
        ret.put(CLIENT_ACCOUNTING, m_accountingString);
        return ret;
    }

    private String getProgramString() {
        return String.format("VSCode connector | Version %s", Version.s_version);
    }
}
