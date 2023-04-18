package com.github.theprez.codefori.requests;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.sound.midi.MetaEventListener;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.google.gson.JsonObject;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400JDBCConnection;
import com.ibm.as400.access.AS400JDBCDataSource;

public class RunSql extends ClientRequest {

    private ResultSet m_rs = null;
    private boolean m_isDone = false;

    public RunSql(DataStreamProcessor _io, SystemConnection m_conn, JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    public void go() throws Exception {
        String sql = getRequestField("sql").getAsString();
        int numRows = super.getRequestFieldInt("rows", 1000);
        AS400JDBCConnection jdbcConn = getSystemConnection().getJdbcConnection();
        Statement stmt = jdbcConn.createStatement();
        boolean hasRs = stmt.execute(sql);
        if (hasRs) {
            m_rs = stmt.getResultSet();
            Map<String, Object> metaData = new LinkedHashMap<String, Object>();
            ResultSetMetaData rsMetaData = m_rs.getMetaData();
            metaData.put("column_count", rsMetaData.getColumnCount());
            metaData.put("job", getSystemConnection().getJdbcJobName());
            List<Object> columnMetaData = new LinkedList<Object>();
            for (int i = 1; i <= rsMetaData.getColumnCount(); ++i) {
                Map<String, Object> columnAttrs = new LinkedHashMap<String, Object>();
                columnAttrs.put("name", rsMetaData.getColumnName(i));
                columnAttrs.put("type", rsMetaData.getColumnTypeName(i));
                columnAttrs.put("display_size", rsMetaData.getColumnDisplaySize(i));
                columnAttrs.put("label", rsMetaData.getColumnLabel(i));
                columnMetaData.add(columnAttrs);
            }
            metaData.put("columns", columnMetaData);
            addReplyData("metadata", metaData);
            List<Object> data = getNextDataBlock(numRows);
            addReplyData("data", data);
            addReplyData("is_done", m_isDone);

        }
    }

    List<Object> getNextDataBlock(int numRows) throws SQLException {
        List<Object> data = new LinkedList<Object>();
        if (m_isDone) {
            return data;
        }
        for (int i = 0; i < numRows; ++i) {
            if (!m_rs.next()) {
                m_isDone = true;
                break;
            }
            List<Object> rowData = new LinkedList<Object>();
            int numCols = m_rs.getMetaData().getColumnCount();
            for (int col = 1; col <= numCols; ++col) {
                rowData.add(m_rs.getObject(col));
            }
            data.add(rowData);
        }
        return data;
    }

    public Object isDone() {
        return m_isDone;
    }

}
