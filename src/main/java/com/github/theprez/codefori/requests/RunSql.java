package com.github.theprez.codefori.requests;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.google.gson.JsonObject;
import com.ibm.as400.access.AS400JDBCConnection;

public class RunSql extends ClientRequest {

    private boolean m_isDone = false;
    private ResultSet m_rs = null;

    public RunSql(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    List<Object> getNextDataBlock(final int numRows) throws SQLException {
        final List<Object> data = new LinkedList<Object>();
        if (m_isDone) {
            return data;
        }
        for (int i = 0; i < numRows; ++i) {
            if (!m_rs.next()) {
                m_isDone = true;
                break;
            }
            final List<Object> rowData = new LinkedList<Object>();
            final int numCols = m_rs.getMetaData().getColumnCount();
            for (int col = 1; col <= numCols; ++col) {
                rowData.add(m_rs.getObject(col));
            }
            data.add(rowData);
        }
        return data;
    }

    @Override
    public void go() throws Exception {
        final String sql = getRequestField("sql").getAsString();
        final int numRows = super.getRequestFieldInt("rows", 1000);
        final AS400JDBCConnection jdbcConn = getSystemConnection().getJdbcConnection();
        final Statement stmt = jdbcConn.createStatement();
        final boolean hasRs = stmt.execute(sql);
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

        }
    }

    public Object isDone() {
        return m_isDone;
    }

}
