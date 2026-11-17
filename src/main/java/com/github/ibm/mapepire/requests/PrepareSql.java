package com.github.ibm.mapepire.requests;

import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.google.gson.JsonObject;
import com.ibm.as400.access.AS400JDBCPreparedStatement;

public class PrepareSql extends BlockRetrievableRequest {

    private PreparedStatement m_stmt = null;
    private final PreparedExecute m_executeTask;

    public PrepareSql(final DataStreamProcessor _io, final SystemConnection _conn, final JsonObject _reqObj,
            final boolean _isImmediateExecute) {
        super(_io, _conn, _reqObj);
        m_executeTask = _isImmediateExecute ? new PreparedExecute(_io, _reqObj, this) : null;
    }

    /**
     * Prepare the requested SQL statement and add its result-set and parameter metadata to the reply. If this
     * request also executes immediately, run the statement and merge the execution reply. The prepared
     * statement stays open after this method returns so that it can be executed again; it is released by
     * {@link #closeResources()}.
     *
     * @throws Exception
     *             if the statement is not allowed on this connection, or cannot be prepared, described, or
     *             executed
     */
    @Override
    public void go() throws Exception {
        final String sql = getRequestField("sql").getAsString();
        final SystemConnection systemConnection = getSystemConnection();
        systemConnection.verifyBasicQueryOnly(sql);
        final Connection jdbcConn = systemConnection.getJdbcConnection();
        final PreparedStatement stmt;
        if (sql.trim().toLowerCase().startsWith("call")) {
            stmt = jdbcConn.prepareCall(sql);
        } else {
            stmt = jdbcConn.prepareStatement(sql);
        }
        synchronized (this) {
            if (m_isReleased) {
                stmt.close();
                throw new SQLException("Request was closed before the statement was prepared");
            }
            m_stmt = stmt;
        }
        try {
            describeStatement();
        } catch (final Exception _e) {
            try {
                closeResources();
            } catch (final SQLException _closeErr) {
                _e.addSuppressed(_closeErr);
            }
            throw _e;
        }
        if (null != m_executeTask) {
            m_executeTask.go();
            m_rs = m_executeTask.m_rs;
            mergeReplyData(m_executeTask);
        }
    }

    /**
     * Add the {@code metadata} reply field, describing the prepared statement's result-set columns and
     * parameters, and the {@code parameter_count} reply field.
     *
     * @throws Exception
     *             if the statement's metadata cannot be retrieved
     */
    private void describeStatement() throws Exception {
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
                columnAttrs.put("precision", rsMetaData.getPrecision(i));
                columnAttrs.put("scale", rsMetaData.getScale(i));
                columnAttrs.put("autoIncrement", rsMetaData.isAutoIncrement(i));
                columnAttrs.put("nullable", rsMetaData.isNullable(i));
                columnAttrs.put("readOnly", rsMetaData.isReadOnly(i));
                columnAttrs.put("writeable", rsMetaData.isWritable(i));
                columnAttrs.put("table", rsMetaData.getTableName(i));
                columnMetaData.add(columnAttrs);
            }
            metaData.put("columns", columnMetaData);
        }

        final ParameterMetaData pMeta = m_stmt.getParameterMetaData();
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
    }

    private static String getDb2ParameterName(final PreparedStatement _stmt, final int _parameterIndex) throws SQLException {
        if (_stmt instanceof AS400JDBCPreparedStatement) {
            return ((AS400JDBCPreparedStatement) _stmt).getDB2ParameterName(_parameterIndex);
        }
        return "?";
    }

    /**
     * Map a JDBC parameter mode to the string sent to clients in the parameter metadata.
     *
     * @param _parameterMode
     *            a {@link ParameterMetaData} {@code parameterMode*} constant
     * @return {@code IN}, {@code OUT}, {@code INOUT}, or {@code UNKNOWN}
     */
    private static String getModeString(final int _parameterMode) {
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

    /**
     * Report whether this request has no more data to fetch. A prepare-only request is always done; a
     * prepare-and-execute request is done when the execution has returned all of its rows.
     *
     * @return {@code true} if no more rows can be fetched for this request
     */
    @Override
    public boolean isDone() {
        if (null != m_executeTask) {
            return m_isDone || m_executeTask.isDone();
        }
        // This means that the request was _only_ for a prepare, so yes we're done if we haven't fetched any data
        return true;
    }

    /**
     * Delegate to the execute task so that any deferred ResultSet close
     * (needed when a large-blob async spool is in progress) is triggered
     * after the WebSocket reply is sent. Without this, m_deferredFetchResult
     * is set on the PreparedExecute instance but processAfterReplySent() would
     * only run on this PrepareSql instance, silently skipping the deferred close.
     */
    @Override
    protected synchronized void processAfterReplySent() {
        super.processAfterReplySent();
        if (null != m_executeTask) {
            m_executeTask.processAfterReplySent();
        }
    }

    /**
     * The prepared statement is kept open after its result set is exhausted so it
     * can be re-executed, so it is only released here (on sqlclose or on failure).
     */
    @Override
    protected synchronized void closeResources() throws SQLException {
        try {
            if (null != m_executeTask) {
                m_executeTask.closeResources();
            }
            super.closeResources();
        } finally {
            if (null != m_stmt && !m_stmt.isClosed()) {
                m_stmt.close();
            }
        }
    }

}
