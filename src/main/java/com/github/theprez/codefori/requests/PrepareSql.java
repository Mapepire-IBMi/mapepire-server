package com.github.theprez.codefori.requests;

import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.google.gson.JsonObject;
import com.ibm.as400.access.AS400JDBCPreparedStatement;

public class PrepareSql extends BlockRetrievableRequest {

    private PreparedStatement m_stmt = null;
    private final PreparedExecute m_executeTask;

    public PrepareSql(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj,
            final boolean _isImmediateExecute) {
        super(_io, m_conn, _reqObj);
        m_executeTask = _isImmediateExecute ? new PreparedExecute(_io, _reqObj, this) : null;
    }

    @Override
    public void go() throws Exception {
        final String sql = getRequestField("sql").getAsString();
        final Connection jdbcConn = getSystemConnection().getJdbcConnection();
        m_stmt = (PreparedStatement) jdbcConn.prepareStatement(sql);
        final Map<String, Object> metaData = new LinkedHashMap<String, Object>();

        final ResultSetMetaData rsMetaData = m_stmt.getMetaData();
        if (null != rsMetaData) {
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
        }

        ParameterMetaData pMeta = m_stmt.getParameterMetaData();
        if (null != pMeta) {
            addReplyData("parameter_count", pMeta.getParameterCount());
            final List<Object> parameterList = new LinkedList<Object>();
            for (int i = 1; i <= pMeta.getParameterCount(); ++i) {
                final Map<String, Object> parmData = new LinkedHashMap<String, Object>();
                parmData.put("type", pMeta.getParameterTypeName(i));
                parmData.put("mode", getModeString(pMeta.getParameterMode(i)));
                parmData.put("precision", pMeta.getPrecision(i));
                parmData.put("scale", pMeta.getScale(i));
                parmData.put("name", getDb2ParameterName(m_stmt, i));
                parameterList.add(parmData);
            }
            metaData.put("parameters", parameterList);
        }

        addReplyData("metadata", metaData);
        if (null != m_executeTask) {
            m_executeTask.go();
            mergeReplyData(m_executeTask);
        }
    }

    private static String getDb2ParameterName(PreparedStatement _stmt, int _i) throws SQLException {
        if (_stmt instanceof AS400JDBCPreparedStatement) {
            return ((AS400JDBCPreparedStatement) _stmt).getDB2ParameterName(_i);
        }
        return "?";
    }

    private String getModeString(int _parameterMode) {
        switch (_parameterMode) {
            case ParameterMetaData.parameterModeIn:
                return "IN";
            case ParameterMetaData.parameterModeOut:
                return "OUT";
            case ParameterMetaData.parameterModeInOut:
                return "INOUT";
            default:
                return "UNKNOWN";
        }
    }

    PreparedStatement getStatement() {
        return m_stmt;
    }

}
