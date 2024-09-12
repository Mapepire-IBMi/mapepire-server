package com.github.ibm.mapepire.requests;

import java.sql.CallableStatement;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.google.gson.JsonObject;
import com.ibm.as400.access.AS400JDBCParameterMetaData;

public abstract class BlockRetrievableRequest extends ClientRequest {

    protected boolean m_isDone = false;
    protected ResultSet m_rs = null;
    protected final boolean m_isTerseData;

    protected BlockRetrievableRequest(DataStreamProcessor _io, SystemConnection _conn, JsonObject _reqObj) {
        super(_io, _conn, _reqObj);
        m_isTerseData = getRequestFieldBoolean("terse", false);
    }

    List<Object> getNextDataBlock(final int _numRows) throws SQLException {
        if (m_isDone) {
            return new LinkedList<Object>();
        }
        DataBlockFetchResult result = getNextDataBlock(m_rs, _numRows, m_isTerseData);
        m_isDone = result.isDone();
        return result.m_data;
    }

    List<Object> getOutputParms(PreparedStatement _stmt) throws SQLException {
        LinkedList<Object> ret = new LinkedList<Object>();
        if (!(_stmt instanceof CallableStatement)) {
            return ret;
        }
        CallableStatement stmt = (CallableStatement) _stmt;
        ParameterMetaData parmMeta = stmt.getParameterMetaData();
        int numParams = parmMeta.getParameterCount();
        for (int i = 1; i <= numParams; ++i) {
            Map<String, Object> parmInfo = new LinkedHashMap<String, Object>();
            parmInfo.put("index", i);
            parmInfo.put("type", parmMeta.getParameterTypeName(i));
            parmInfo.put("precision", parmMeta.getPrecision(i));
            parmInfo.put("scale", parmMeta.getScale(numParams));
            if (parmMeta instanceof AS400JDBCParameterMetaData) {
                AS400JDBCParameterMetaData db2ParmMeta = (AS400JDBCParameterMetaData) parmMeta;
                parmInfo.put("name", db2ParmMeta.getDB2ParameterName(i));
                parmInfo.put("ccsid", db2ParmMeta.getParameterCCSID(i));
            }
            int parmMode = parmMeta.getParameterMode(i);
            if (ParameterMetaData.parameterModeOut == parmMode || ParameterMetaData.parameterModeInOut == parmMode) {
                Object jsonValue = null;
                Object value = stmt.getObject(i);
                if (value == null) {
                    jsonValue = null;
                } else if (value instanceof CharSequence) {
                    jsonValue = value.toString().trim();
                } else if (value instanceof Number || value instanceof Boolean) {
                    jsonValue = value;
                } else {
                    jsonValue = stmt.getString(i);
                }
                parmInfo.put("value", jsonValue);
            }
            ret.add(parmInfo);
        }

        return ret;
    }

    protected static class DataBlockFetchResult {
        private final List<Object> m_data = new LinkedList<Object>();
        private boolean m_isDone = false;

        private DataBlockFetchResult setDone(final boolean _b) {
            m_isDone = _b;
            return this;
        }

        public boolean isDone() {
            return m_isDone;
        }

        private void add(final Object _o) {
            m_data.add(_o);
        }

        public Object getData() {
            return m_data;
        }
    }

    protected static DataBlockFetchResult getNextDataBlock(final ResultSet _rs, final int _numRows,
            final boolean _isTerseDataFormat) throws SQLException {
        final DataBlockFetchResult ret = new DataBlockFetchResult();

        if (null == _rs) {
            throw new SQLException("Result set was null");
        }
        if (_rs.isClosed()) {
            return ret.setDone(true);
        }
        for (int i = 0; i < _numRows; ++i) {
            if (!_rs.next()) {
                ret.setDone(true);
                Statement s = _rs.getStatement();
                if (s instanceof PreparedStatement) {
                    _rs.close();
                } else {
                    _rs.getStatement().close();
                }
                break;
            }
            final LinkedHashMap<String, Object> mapRowData = new LinkedHashMap<String, Object>();
            final LinkedList<Object> terseRowData = new LinkedList<Object>();
            final int numCols = _rs.getMetaData().getColumnCount();
            for (int col = 1; col <= numCols; ++col) {
                String column = _rs.getMetaData().getColumnName(col);
                Object cellData = _rs.getObject(col);
                Object cellDataForResponse = null;
                if (null == cellData) {
                    cellDataForResponse = null;
                } else if (cellData instanceof CharSequence) {
                    cellDataForResponse = cellData.toString().trim();
                } else if (cellData instanceof Number || cellData instanceof Boolean) {
                    cellDataForResponse = cellData;
                } else {
                    cellDataForResponse = _rs.getString(col);
                }
                if (_isTerseDataFormat) {
                    terseRowData.add(cellDataForResponse);
                } else {
                    mapRowData.put(column, cellDataForResponse);
                }
            }
            ret.add(_isTerseDataFormat ? terseRowData : mapRowData);
        }
        return ret;
    }

    public boolean isDone() {
        return m_isDone;
    }

    protected Map<String, Object> getResultMetaDataForResponse() throws SQLException {
        return getResultMetaDataForResponse(this.m_rs.getMetaData(), getSystemConnection());
    }

    public static Map<String, Object> getResultMetaDataForResponse(ResultSetMetaData _md, SystemConnection _conn)
            throws SQLException {
        final Map<String, Object> metaData = new LinkedHashMap<String, Object>();
        metaData.put("column_count", _md.getColumnCount());
        metaData.put("job", _conn.getJdbcJobName());
        final List<Object> columnMetaData = new LinkedList<Object>();
        for (int i = 1; i <= _md.getColumnCount(); ++i) {
            final Map<String, Object> columnAttrs = new LinkedHashMap<String, Object>();
            columnAttrs.put("name", _md.getColumnName(i));
            columnAttrs.put("type", _md.getColumnTypeName(i));
            columnAttrs.put("display_size", _md.getColumnDisplaySize(i));
            columnAttrs.put("label", _md.getColumnLabel(i));
            columnAttrs.put("precision", _md.getPrecision(i));
            columnAttrs.put("scale", _md.getScale(i));
            columnMetaData.add(columnAttrs);
        }
        metaData.put("columns", columnMetaData);
        return metaData;
    }

}
