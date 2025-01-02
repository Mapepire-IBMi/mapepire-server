package com.github.ibm.mapepire;

import java.util.Properties;

import com.github.theprez.jcmdutils.StringUtils;

public class ClientSpecialRegistersVSCode implements ClientSpecialRegisters {
    final String m_clientIP;
    final String m_accountingString;

    public ClientSpecialRegistersVSCode() {
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
            location = ClientSpecialRegistersVSCode.class.getProtectionDomain().getCodeSource().getLocation().toString();
        } catch (Exception e) {
            Tracer.err(e);
        }
        m_accountingString = "location: "+location;
    }

    @Override
    public Properties getProperties(final String _applicationName) {
        Properties ret = new Properties();
        ret.put(CLIENT_USER, System.getProperty("user.name", "<unknown>"));
        ret.put(CLIENT_APP_NAME, _applicationName);
        ret.put(CLIENT_HOST_NAME, m_clientIP);
        ret.put(CLIENT_PGM_ID, getProgramString());
        ret.put(CLIENT_ACCOUNTING, m_accountingString);
        return ret;
    }

    private String getProgramString() {
        return String.format("VSCode connector | Version %s", Version.s_version);
    }
}
