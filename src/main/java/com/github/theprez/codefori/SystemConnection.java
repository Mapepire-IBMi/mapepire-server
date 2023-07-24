package com.github.theprez.codefori;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import com.ibm.as400.access.AS400JDBCDriver;

public class SystemConnection {
    public static class ConnectionOptions {

        public enum ConnectionMethod {
            TCP,
            CLI;

            public String getConnectionString() {
                if (!isRunningOnIBMi()) {
                    String hostname = "mysystem";
                    String username = "user";
                    String password = "pw";
                    return String.format("jdbc:as400:%s;user=%s;password=%s", hostname, username, password);
                }
                switch (this) {
                    case CLI:
                        return Boolean.getBoolean("jdbc.db2.restricted.local.connection.only") ? "jdbc:default:connection":"jdbc:db2:localhost";
                    default:
                        return "jdbc:as400:localhost";
                }
            }
        }

        private String m_jdbcProps = "";
        private ConnectionMethod m_connectionMethod = ConnectionMethod.CLI;

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

    }

    private Connection m_conn;
    private ConnectionOptions m_connectionOptions = null;

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
            Class<Connection> connectionClass = (Class<Connection>) c.getClass();
            boolean isNativeDriver = connectionClass.getSimpleName().equalsIgnoreCase("DB2Connection");
            String methodName = isNativeDriver ? "getServerJobName" : "getServerJobIdentifier";
            String driverSuppliedName = c.getClass()
                    .getMethod(methodName).invoke(c).toString();
            return isNativeDriver ? driverSuppliedName : makePrettyJobNameFromJt400Name(driverSuppliedName);
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

    public synchronized Connection reconnect(final ConnectionOptions _opts) throws SQLException {
        if (null != m_conn) {
            final Connection cpy = m_conn;
            m_conn = null;
            cpy.close();
        }
        try {
            DriverManager.registerDriver(new AS400JDBCDriver());
            m_connectionOptions = _opts;
            return m_conn = DriverManager.getConnection(_opts.getConnectionMethod().getConnectionString() + ";" + _opts.getJdbcProperties());
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }
}
