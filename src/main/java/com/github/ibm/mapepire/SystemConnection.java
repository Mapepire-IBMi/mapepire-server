package com.github.ibm.mapepire;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.github.theprez.jcmdutils.StringUtils;
import com.ibm.as400.access.AS400JDBCConnection;
import com.ibm.as400.access.AS400JDBCDriver;

public class SystemConnection {
    public static class ConnectionOptions {
        private String host;
        private String userProfile;
        private String password;

        public ConnectionOptions() {
        }

        public void setHost(String _host) {
            host = _host;
        }

        public String getHost() {
            if (host == null) {
                return "localhost";
            } else {
                return this.host;
            }
        }

        public void setUserProfile(String _user) {
            this.userProfile = _user;
        }

        public void setPassword(String _pass) {
            this.password = _pass;
        }

        public enum ConnectionMethod {
            TCP,
            CLI;
        }

        private String getAuthString() throws IOException {

            if (!MapepireServer.isSingleMode()) {
                if (StringUtils.isEmpty(userProfile) || userProfile.contains("*")) {
                    throw new IOException("Invalid Username");
                }
                if (StringUtils.isEmpty(password)) {
                    throw new IOException("Invalid Password");
                }
            }
            if (userProfile == null || password == null) {
                return getHost();
            } else {
                return String.format("%s;user=%s;password=%s", getHost(), userProfile, password);
            }
        }

        public String getConnectionString(ConnectionMethod method) throws IOException {
            if (!isRunningOnIBMi()) {
                return String.format("jdbc:as400:" + this.getAuthString());
            }
            switch (method) {
                case CLI:
                    return Boolean.getBoolean("jdbc.db2.restricted.local.connection.only")
                            ? "jdbc:default:connection"
                            : "jdbc:db2:" + this.getAuthString();
                default:
                    return "jdbc:as400:" + this.getAuthString();
            }
        }

        private String m_jdbcProps = "";
        private ConnectionMethod m_connectionMethod = ConnectionMethod.CLI;
        private ClientSpecialRegisters m_clientRegs = new ClientSpecialRegisters();

        String getJdbcProperties() {
            return m_jdbcProps;
        }

        public void setConnectionMethod(ConnectionMethod _m) {
            m_connectionMethod = _m;
        }

        public void setJdbcProperties(final String _props) {
            m_jdbcProps = _props;
        }

        public ConnectionMethod getConnectionMethod() {
            return m_connectionMethod;
        }

        public void setCSRApplicationName(String _appName) {
            m_clientRegs.setApplicationName(_appName);
        }
    }

    private Connection m_conn;
    private ConnectionOptions m_connectionOptions = null;
    private String host;
    private String userProfile;
    private String password;

    public SystemConnection() throws IOException {
        if (!MapepireServer.isSingleMode()) {
            throw new IOException("Improper usage");
        }
    }

    public SystemConnection(String host, String user, String pass) throws IOException {
        super();
        this.host = host;
        if (StringUtils.isEmpty(user) || user.contains("*")) {
            throw new IOException("Invalid Username");
        }
        if (StringUtils.isEmpty(host) || host.contains("*")) {
            throw new IOException("Invalid Hostname");
        }
        if (StringUtils.isEmpty(pass)) {
            throw new IOException("Invalid Password");
        }
        this.userProfile = user;
        this.password = pass;
    }

    public static boolean isRunningOnIBMi() {
        return System.getProperty("os.name", "").contains("400");
    }

    public synchronized Connection getJdbcConnection() throws SQLException {
        if (null != m_conn && !m_conn.isClosed()) {
            return m_conn;
        }
        if (Boolean.getBoolean("codeserver.jdbc.autoconnect")) {
            return reconnect(m_connectionOptions);
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

    public synchronized Connection reconnect(final ConnectionOptions _opts) throws SQLException {
        if (null != m_conn) {
            final Connection cpy = m_conn;
            m_conn = null;
            cpy.close();
        }
        try {
            DriverManager.registerDriver(new AS400JDBCDriver());
            m_connectionOptions = _opts;

            m_connectionOptions.setHost(this.host);
            m_connectionOptions.setUserProfile(this.userProfile);
            m_connectionOptions.setPassword(this.password);

            m_conn = DriverManager.getConnection(m_connectionOptions.getConnectionString(m_connectionOptions.m_connectionMethod) + ";" + _opts.getJdbcProperties());
            m_conn.setClientInfo(_opts.m_clientRegs.getProperties());
            return m_conn;
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }
}
