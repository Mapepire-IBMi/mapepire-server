package com.github.theprez.codefori;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.github.theprez.jcmdutils.StringUtils;

public class SystemConnection {
    public static class ConnectionOptions {

        public enum ConnectionMethod {
            TCP,
            UNIX,
            CLI;

            public String getJarURL() throws MalformedURLException {
                if (!isRunningOnIBMi()) {
                    return "";
                }
                switch (this) {
                    case TCP:
                        return "file:////QIBM/ProdData/OS400/jt400/lib/jt400.jar";
                    case UNIX:
                        return "file:////QIBM/ProdData/OS400/jt400/lib/jt400Native.jar";
                    default:
                        return "file:///QIBM/ProdData/Java400/ext/db2_classes.jar";
                }
            }

            public String getDriverClassName() {
                if (!isRunningOnIBMi()) {
                    return "com.ibm.as400.access.AS400JDBCDriver";
                }
                switch (this) {
                    case CLI:
                        return "com.ibm.db2.jdbc.app.DB2Driver";
                    default:
                        return "com.ibm.as400.access.AS400JDBCDriver";
                }
            }

            public String getConnectionString() {
                if (!isRunningOnIBMi()) {
                    String hostname = "mysystem";
                    String username = "user";
                    String password = "pw";
                    return String.format("jdbc:as400:%s;user=%s;password=%s", hostname, username, password);
                }
                switch (this) {
                    case CLI:
                        return "jdbc:db2:localhost";
                    default:
                        return "jdbc:as400:localhost";
                }
            }
        }

        private String m_jdbcProps = "";
        private ConnectionMethod m_connectionMethod = ConnectionMethod.TCP;

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
    private static Map<String, URLClassLoader> s_classLoaderMap = new HashMap<String, URLClassLoader>(3);

    public static boolean isRunningOnIBMi() {
        return System.getProperty("os.name", "").contains("400");
    }

    private static URLClassLoader getClassLoaderForFile(final String _fileUrl) throws MalformedURLException {
        URLClassLoader ret = s_classLoaderMap.get(_fileUrl);
        if (null != ret) {
            return ret;
        }
        ret = new URLClassLoader(StringUtils.isEmpty(_fileUrl) ? new URL[] {} : new URL[] { new URL(_fileUrl) });
        s_classLoaderMap.put(_fileUrl, ret);
        return ret;
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
            String methodName = isNativeDriver ? "getServerJobName" :"getServerJobIdentifier";
            String driverSuppliedName =  c.getClass()
                    .getMethod(methodName).invoke(c).toString();
            return isNativeDriver ? driverSuppliedName: makePrettyJobName(driverSuppliedName);
        } catch (Exception e) {
            e.printStackTrace();
            Tracer.err(e);
            return "??????/??????/??????";
        }
    }

    private String makePrettyJobName(final String _jobString) {
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
            URLClassLoader cl = getClassLoaderForFile(_opts.getConnectionMethod().getJarURL());
            cl.setDefaultAssertionStatus(false);
            final Class<Driver> dsClass = (Class<Driver>) cl
                    .loadClass(_opts.getConnectionMethod().getDriverClassName());
            DriverManager.registerDriver(dsClass.getDeclaredConstructor().newInstance());
            m_connectionOptions = _opts;
            return m_conn = DriverManager
                    .getConnection(_opts.getConnectionMethod().getConnectionString() + ";" + _opts.getJdbcProperties());
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }
}
