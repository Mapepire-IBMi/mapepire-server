package com.github.theprez.codefori;

import java.io.IOException;
import java.sql.SQLException;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.ConsoleQuestionAsker;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400JDBCConnection;
import com.ibm.as400.access.AS400JDBCDataSource;

public class SystemConnection {
    private final AS400 m_as400;
    private AS400JDBCConnection m_conn;

    public SystemConnection(final AppLogger _logger) throws IOException {
        if (System.getProperty("os.name", "").equalsIgnoreCase("OS/400")) {
            m_as400 = new AS400("localhost", "*CURRENT", "*CURRENT".toCharArray());
        } else {
            ConsoleQuestionAsker asker = new ConsoleQuestionAsker();
            String host = asker.askNonEmptyStringQuestion(_logger, null, "Enter system name: ");
            String uid = asker.askNonEmptyStringQuestion(_logger, null, "Enter user id: ");
            String pw = asker.askUserForPwd("Password: ");
            m_as400 = new AS400(host, uid, pw.toCharArray());
        }
    }

    public synchronized AS400JDBCConnection getJdbcConnection() throws SQLException {
        if (null != m_conn && !m_conn.isClosed()) {
            return m_conn;
        }
        if (Boolean.getBoolean("codeserver.jdbc.autoconnect")) {
            AS400JDBCDataSource ds = new AS400JDBCDataSource(m_as400);
            return m_conn = (AS400JDBCConnection) ds.getConnection();
        }
        throw new SQLException("Not connected");
    }

    public static class ConnectionOptions {

        private String m_jdbcProps;

        public void setJdbcProperties(String _props) {
            m_jdbcProps = _props;
        }

        String getJdbcProperties() {
            return m_jdbcProps;
        }

    }

    public synchronized AS400JDBCConnection reconnect(ConnectionOptions _opts) throws SQLException {
        if (null != m_conn) {
            AS400JDBCConnection cpy = m_conn;
            m_conn = null;
            cpy.close();
        }
        AS400JDBCDataSource ds = new AS400JDBCDataSource(m_as400);
        ds.setProperties(_opts.getJdbcProperties());
        return m_conn = (AS400JDBCConnection) ds.getConnection();
    }

    public AS400 getAs400() {
        return m_as400;
    }

    public String getJdbcJobName() throws SQLException {
        return makePrettyJobName(getJdbcConnection().getServerJobIdentifier());
    }

    private String makePrettyJobName(String _jobString) {
        String name = _jobString.substring(0, 10).trim();
        String user = _jobString.substring(10, 20).trim();
        String number = _jobString.substring(20).trim();
        return String.format("%s/%s/%s", number, user, name);
    }
}
