package com.github.theprez.codefori.requests;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.google.gson.JsonObject;

public class RunSql extends BlockRetrievableRequest {

    public RunSql(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    @Override
    public void go() throws Exception {
        final String sql = getRequestField("sql").getAsString();
        final int numRows = super.getRequestFieldInt("rows", 1000);
        final Connection jdbcConn = getSystemConnection().getJdbcConnection();
        final Statement stmt = jdbcConn.createStatement(); //TODO: look into using prepared statements for performance
        final boolean hasRs = stmt.execute(sql);
        addReplyData("has_results", hasRs);
        addReplyData("update_count", stmt.getLargeUpdateCount());
        if (hasRs) {
            m_rs = stmt.getResultSet();
            addReplyData("metadata", getResultMetaDataForResponse());
            final List<Object> data = getNextDataBlock(numRows);
            addReplyData("data", data);
            addReplyData("is_done", m_isDone);
        }
    }
}
