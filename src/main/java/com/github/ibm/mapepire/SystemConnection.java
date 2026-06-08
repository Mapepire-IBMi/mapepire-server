package com.github.ibm.mapepire;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import com.github.ibm.mapepire.authfile.AuthFile;
import com.github.theprez.jcmdutils.StringUtils;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400JDBCConnection;
import com.ibm.as400.access.AS400JDBCDriver;

public class SystemConnection {
    public enum ConnectionMethod {
        TCP, CLI;
    }

    private Connection m_conn;
    private ConnectionMethod m_connectionMethod = ConnectionMethod.CLI;
    private String m_jdbcProps = "";
    private String host;
    private String userProfile;
    private char[] password;
    private final ClientSpecialRegisters m_clientRegs;
    private String m_applicationName;
    private final String clientAddress;
    // Raw Base64 "user:pass" from the WebSocket Authorization header.
    // Stored so BlobStore can validate HTTP /blob/{token} requests.
    private String m_rawCredentials = null;

    public SystemConnection() throws IOException {
        if (!MapepireServer.isSingleMode()) {
            throw new IOException("Improper usage");
        }
        ClientSpecialRegistersVSCode clientRegs = new ClientSpecialRegistersVSCode();
        this.m_clientRegs = clientRegs;
        this.clientAddress = clientRegs.getClientAddress();
        this.userProfile = System.getProperty("user.name");
    }

    public SystemConnection(String clientHost, String clientAddress, String host, String user, char[] pass) throws IOException {
        super();
        if (MapepireServer.isSingleMode()) {
            throw new IOException("Improper usage");
        }
        this.host = host;
        if (StringUtils.isEmpty(user) || user.contains("*")) {
            throw new IOException("Invalid Username");
        }
        if (StringUtils.isEmpty(host) || host.contains("*")) {
            throw new IOException("Invalid Hostname");
        }
        if (pass == null || pass.length == 0) {
            throw new IOException("Invalid Password");
        }
        this.userProfile = user;
        this.password = pass;
        this.clientAddress = clientAddress;
        this.m_clientRegs = new ClientSpecialRegistersRemote(clientHost, clientAddress, user);
    }

    /** Returns the raw Base64 Authorization credentials, or {@code null} in single mode. */
    public String getRawCredentials() {
        return m_rawCredentials;
    }

    /** Called by {@link com.github.ibm.mapepire.ws.DbWebsocketClient} at connection time. */
    public void setRawCredentials(String rawCredentials) {
        this.m_rawCredentials = rawCredentials;
    }

    public static boolean isRunningOnIBMi() {
        return System.getProperty("os.name", "").contains("400");
    }

    public synchronized Connection getJdbcConnection() throws SQLException {
        if (null != m_conn && !m_conn.isClosed()) {
            return m_conn;
        }
        if (Boolean.getBoolean("codeserver.jdbc.autoconnect")) {
            return reconnect(m_connectionMethod, m_jdbcProps, m_applicationName);
        }
        throw new SQLException("Not connected");
    }

    public String getJdbcJobName() throws SQLException {
        try {
            Connection c = getJdbcConnection();
            if (c instanceof AS400JDBCConnection) {
                return makePrettyJobNameFromJt400Name(((AS400JDBCConnection) c).getServerJobIdentifier());
            }
            return c.getClass().getMethod("getServerJobName").invoke(c).toString();
        } catch (Exception e) {
            Tracer.err(e);
            return "??????/??????/??????";
        }
    }

    private String makePrettyJobNameFromJt400Name(final String _jobString) {
        final String name = _jobString.substring(0, 10).trim();
        final String user = _jobString.substring(10, 20).trim();
        final String number = _jobString.substring(20).trim();
        return String.format("%s/%s/%s", number, user, name);
    }

    public synchronized void close() {
        if (null != m_conn) {
            try {
                m_conn.close();
            } catch (SQLException e) {
                Tracer.err(e);
            }
            m_conn = null;
        }
    }

    public synchronized Connection reconnect(final ConnectionMethod _connectionMethod, final String _jdbcProps, final String _applicationName) throws SQLException {
        if (null != m_conn) {
            final Connection cpy = m_conn;
            m_conn = null;
            cpy.close();
        }
        if (StringUtils.isNonEmpty(_applicationName)) {
            m_applicationName = _applicationName;
        }
        
        // Store connection settings
        m_connectionMethod = _connectionMethod;
        m_jdbcProps = _jdbcProps;
        
        try {
            // check if this connection is allowed by our security rules file
            AuthFile.getDefault().verify(this.userProfile, this.clientAddress);

            // Create AS400 object
            AS400 as400System;
            String systemName = (this.host != null) ? this.host : "localhost";
            
            if (this.userProfile != null && this.password != null) {
                as400System = new AS400(systemName, this.userProfile, this.password);
            } else {
                as400System = new AS400(systemName);
            }

            // Parse JDBC properties into Properties object
            Properties jdbcProps = new Properties();
            if (StringUtils.isNonEmpty(_jdbcProps)) {
                String[] propPairs = _jdbcProps.split(";");
                for (String pair : propPairs) {
                    if (StringUtils.isNonEmpty(pair)) {
                        String[] keyValue = pair.split("=", 2);
                        if (keyValue.length == 2) {
                            jdbcProps.setProperty(keyValue[0].trim(), keyValue[1].trim());
                        }
                    }
                }
            }

            // Create AS400JDBCConnection from AS400 object
            AS400JDBCDriver driver = new AS400JDBCDriver();
            
            // Connect with null database name (database name should only be used for IASP connections)
            m_conn = driver.connect(as400System, jdbcProps, null);
            m_conn.setClientInfo(this.m_clientRegs.getProperties(_applicationName));
            return m_conn;
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }
}
