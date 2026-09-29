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
        final int numRows = super.getRequestFieldInt("rows", 1000);
        final Connection jdbcConn = getSystemConnection().getJdbcConnection();
        m_stmt = jdbcConn.createStatement(); //TODO: look into using prepared statements for performance
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
        super.closeResources();
        if (null != m_stmt && !m_stmt.isClosed()) {
            m_stmt.close();
        }
    }
}
