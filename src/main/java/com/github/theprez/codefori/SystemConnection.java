package com.github.theprez.codefori;

import java.io.IOException;
import java.sql.SQLException;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.ConsoleQuestionAsker;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400JDBCConnection;
import com.ibm.as400.access.AS400JDBCDataSource;

public class SystemConnection {
    public static class ConnectionOptions {

        private String m_jdbcProps;

        String getJdbcProperties() {
            return m_jdbcProps;
        }

        public void setJdbcProperties(final String _props) {
            m_jdbcProps = _props;
        }

    }

    private final AS400 m_as400;

    private AS400JDBCConnection m_conn;

    public SystemConnection() throws IOException {
        if (System.getProperty("os.name", "").equalsIgnoreCase("OS/400")) {
            m_as400 = new AS400("localhost", "*CURRENT", "*CURRENT");
        } else {
            final AppLogger logger = AppLogger.getSingleton(false);
            final ConsoleQuestionAsker asker = new ConsoleQuestionAsker();
            final String host = asker.askNonEmptyStringQuestion(logger, null, "Enter system name: ");
            final String uid = asker.askNonEmptyStringQuestion(logger, null, "Enter user id: ");
            final String pw = asker.askUserForPwd("Password: ");
            m_as400 = new AS400(host, uid, pw);
        }
    }

    public AS400 getAs400() {
        return m_as400;
    }

    public synchronized AS400JDBCConnection getJdbcConnection() throws SQLException {
        if (null != m_conn && !m_conn.isClosed()) {
            return m_conn;
        }
        if (Boolean.getBoolean("codeserver.jdbc.autoconnect")) {
            final AS400JDBCDataSource ds = new AS400JDBCDataSource(m_as400);
            return m_conn = (AS400JDBCConnection) ds.getConnection();
        }
        throw new SQLException("Not connected");
    }

    public String getJdbcJobName() throws SQLException {
        return makePrettyJobName(getJdbcConnection().getServerJobIdentifier());
    }

    private String makePrettyJobName(final String _jobString) {
        final String name = _jobString.substring(0, 10).trim();
        final String user = _jobString.substring(10, 20).trim();
        final String number = _jobString.substring(20).trim();
        return String.format("%s/%s/%s", number, user, name);
    }

    public synchronized AS400JDBCConnection reconnect(final ConnectionOptions _opts) throws SQLException {
        if (null != m_conn) {
            final AS400JDBCConnection cpy = m_conn;
            m_conn = null;
            cpy.close();
        }
        final AS400JDBCDataSource ds = new AS400JDBCDataSource(m_as400);
        ds.setProperties(_opts.getJdbcProperties());
        return m_conn = (AS400JDBCConnection) ds.getConnection();
    }
}
