package com.github.theprez.codefori.requests;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.google.gson.JsonObject;

public abstract class BlockRetrievableRequest extends ClientRequest {

    protected boolean m_isDone = false;
    protected ResultSet m_rs = null;

    protected BlockRetrievableRequest(DataStreamProcessor _io, SystemConnection _conn, JsonObject _reqObj) {
        super(_io, _conn, _reqObj);
    }

    List<Object> getNextDataBlock(final int numRows) throws SQLException {
        final List<Object> data = new LinkedList<Object>();
        if (m_isDone) {
            return data;
        }
        // TODO: null check m_rs
        for (int i = 0; i < numRows; ++i) {
            if (!m_rs.next()) {
                m_isDone = true;
                Statement s = m_rs.getStatement();
                if (s instanceof PreparedStatement) {
                    m_rs.close();
                } else {
                    m_rs.getStatement().close();
                }
                break;
            }
            final LinkedHashMap<String, Object> rowData = new LinkedHashMap<String, Object>();
            final int numCols = m_rs.getMetaData().getColumnCount();
            for (int col = 1; col <= numCols; ++col) {
                String column = m_rs.getMetaData().getColumnName(col);
                Object cellData = m_rs.getObject(col);
                if (null == cellData) {
                    rowData.put(column, null);
                } else if (cellData instanceof String) {
                    rowData.put(column, ((String) cellData).trim());
                } else if (cellData instanceof Number || cellData instanceof Boolean) {
                    rowData.put(column, cellData);
                } else {
                    rowData.put(column, m_rs.getString(col));
                }
            }
            data.add(rowData);
        }
        return data;
    }

    public Object isDone() {
        return m_isDone;
    }

}
