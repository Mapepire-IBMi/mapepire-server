package com.github.ibm.mapepire;

import java.util.Properties;

public class ClientSpecialRegistersRemote implements ClientSpecialRegisters {

    final String m_clientIP;
    final String m_accountingString;
    private String m_user;

    public ClientSpecialRegistersRemote(final String _clientHost, final String _clientIP, final String _user) {
        m_clientIP = _clientIP;
        m_user = _user;
        m_accountingString = "hostname: " + _clientHost;
    }

    @Override
    public Properties getProperties(final String _applicationName) {
        Properties ret = new Properties();
        ret.put(CLIENT_USER, m_user.toUpperCase());
        ret.put(CLIENT_APP_NAME, null == _applicationName ? "unspecified" : _applicationName);
        ret.put(CLIENT_HOST_NAME, m_clientIP);
        ret.put(CLIENT_PGM_ID, getProgramString());
        ret.put(CLIENT_ACCOUNTING, m_accountingString);
        return ret;
    }

    private String getProgramString() {
        return String.format("Mapepire server connector | Version %s", Version.s_version);
    }
}
