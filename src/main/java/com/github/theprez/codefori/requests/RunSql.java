package com.github.theprez.codefori.requests;

import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.google.gson.JsonObject;
import com.ibm.as400.access.AS400JDBCConnection;

public class RunSql extends BlockRetrievableRequest {

    public RunSql(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    @Override
    public void go() throws Exception {
        final String sql = getRequestField("sql").getAsString();
        final int numRows = super.getRequestFieldInt("rows", 1000);
        final AS400JDBCConnection jdbcConn = getSystemConnection().getJdbcConnection();
        final Statement stmt = jdbcConn.createStatement(); //TODO: look into using prepared statements for performance
        final boolean hasRs = stmt.execute(sql);
        addReplyData("has_results", hasRs);
        if (hasRs) {
            m_rs = stmt.getResultSet();
            final Map<String, Object> metaData = new LinkedHashMap<String, Object>();
            final ResultSetMetaData rsMetaData = m_rs.getMetaData();
            metaData.put("column_count", rsMetaData.getColumnCount());
            metaData.put("job", getSystemConnection().getJdbcJobName());
            final List<Object> columnMetaData = new LinkedList<Object>();
            for (int i = 1; i <= rsMetaData.getColumnCount(); ++i) {
                final Map<String, Object> columnAttrs = new LinkedHashMap<String, Object>();
                columnAttrs.put("name", rsMetaData.getColumnName(i));
                columnAttrs.put("type", rsMetaData.getColumnTypeName(i));
                columnAttrs.put("display_size", rsMetaData.getColumnDisplaySize(i));
                columnAttrs.put("label", rsMetaData.getColumnLabel(i));
                columnMetaData.add(columnAttrs);
            }
            metaData.put("columns", columnMetaData);
            addReplyData("metadata", metaData);
            final List<Object> data = getNextDataBlock(numRows);
            addReplyData("data", data);
            addReplyData("is_done", m_isDone);
        } else {
            m_rs.close();
        }
    }

    public Object isDone() {
        return m_isDone;
    }

}
