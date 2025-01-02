package com.github.ibm.mapepire;

import java.util.Properties;

public interface ClientSpecialRegisters {
    static final String CLIENT_APP_NAME = "ApplicationName"; // AS400JDBCConnectionImpl.applicationNamePropertyName_
    static final String CLIENT_USER = "ClientUser"; // AS400JDBCConnectionImpl.clientUserPropertyName_
    static final String CLIENT_HOST_NAME = "ClientHostname"; // AS400JDBCConnectionImpl.clientHostnamePropertyName_
    static final String CLIENT_ACCOUNTING = "ClientAccounting"; // AS400JDBCConnectionImpl.clientAccountingPropertyName_
    static final String CLIENT_PGM_ID = "ClientProgramID"; // AS400JDBCConnectionImpl.clientProgramIDPropertyName_

    public Properties getProperties(final String _applicationName);
}
