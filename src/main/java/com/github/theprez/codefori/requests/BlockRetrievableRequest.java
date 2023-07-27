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
    private final boolean m_isTerseData;

    protected BlockRetrievableRequest(DataStreamProcessor _io, SystemConnection _conn, JsonObject _reqObj) {
        super(_io, _conn, _reqObj);
        m_isTerseData = getRequestFieldBoolean("terse", false);
    }

    List<Object> getNextDataBlock(final int numRows) throws SQLException {
        final List<Object> data = new LinkedList<Object>();
        if (m_isDone) {
            return data;
        }
        if (null == m_rs) {
            throw new SQLException("Result set was null");
        }
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
            final LinkedHashMap<String, Object> mapRowData = new LinkedHashMap<String, Object>();
            final LinkedList<Object> terseRowData = new LinkedList<Object>();
            final int numCols = m_rs.getMetaData().getColumnCount();
            for (int col = 1; col <= numCols; ++col) {
                String column = m_rs.getMetaData().getColumnName(col);
                Object cellData = m_rs.getObject(col);
                Object cellDataForResponse = null;
                if (null == cellData) {
                    cellDataForResponse = null;
                } else if (cellData instanceof String) {
                    cellDataForResponse=((String) cellData).trim();
                } else if (cellData instanceof Number || cellData instanceof Boolean) {
                    cellDataForResponse = cellData;
                } else {
                    cellDataForResponse = m_rs.getString(col);
                }
                if(m_isTerseData) {
                    terseRowData.add(cellDataForResponse);
                }else {
                    mapRowData.put(column, cellDataForResponse);
                }
            }
            data.add(m_isTerseData ? terseRowData: mapRowData);
        }
        return data;
    }

    public Object isDone() {
        return m_isDone;
    }

}
