package com.github.ibm.mapepire.requests;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.google.gson.JsonObject;

public class RunSql extends BlockRetrievableRequest {

    private Statement m_stmt = null;

    public RunSql(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    @Override
    public void go() throws Exception {
        final String sql = getRequestField("sql").getAsString();
        final SystemConnection systemConnection = getSystemConnection();
        systemConnection.verifyBasicQueryOnly(sql);
        final int numRows = super.getRequestFieldInt("rows", 1000);
        final Connection jdbcConn = systemConnection.getJdbcConnection();
        // A plain Statement is used deliberately. Always routing this request through a PreparedStatement
        // was considered, but there is no universal benefit: for one-shot statement text the extra prepare
        // step buys nothing, and any gain depends on whether the same statement is executed again. That
        // prepare-and-reuse decision belongs to the application, which can use the prepare_sql request
        // when it intends to run a statement repeatedly.
        final Statement stmt = jdbcConn.createStatement();
        synchronized (this) {
            if (m_isReleased) {
                stmt.close();
                throw new SQLException("Request was closed before the statement ran");
            }
            m_stmt = stmt;
        }
        try {
            final boolean hasRs = m_stmt.execute(sql);
            addReplyData("has_results", hasRs);
            addReplyData("update_count", m_stmt.getLargeUpdateCount());
            if (hasRs) {
                m_rs = m_stmt.getResultSet();
                addReplyData("metadata", getResultMetaDataForResponse());
                final List<Object> data = getNextDataBlock(numRows);
                addReplyData("data", data);
                addReplyData("is_done", isDone());
            } else {
                m_stmt.close();
            }
        } catch (final Exception _e) {
            try {
                closeResources();
            } catch (final SQLException _closeErr) {
                _e.addSuppressed(_closeErr);
            }
            throw _e;
        }
    }

    @Override
    protected synchronized void closeResources() throws SQLException {
        try {
            super.closeResources();
        } finally {
            if (null != m_stmt && !m_stmt.isClosed()) {
                m_stmt.close();
            }
        }
    }
}
