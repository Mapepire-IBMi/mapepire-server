package com.github.ibm.mapepire.requests;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.MapepireServer;
import com.github.ibm.mapepire.SystemConnection;
import com.github.ibm.mapepire.Tracer;
import com.github.ibm.mapepire.http.BlobStore;
import com.google.gson.JsonObject;
import com.ibm.as400.access.AS400JDBCParameterMetaData;

public abstract class BlockRetrievableRequest extends ClientRequest {

    protected boolean m_isDone = false;
    protected ResultSet m_rs = null;
    protected final boolean m_isTerseData;

    /** Holds a deferred ResultSet close (with pending spool entries) until after the WS reply is sent. */
    private DataBlockFetchResult m_deferredFetchResult = null;

    /**
     * Tracks large output-param BLOBs that are being async-spooled.
     * Drained in processAfterReplySent() alongside result-set spools.
     */
    private final List<BlobStore.BlobEntry> m_pendingOutputParamSpools = new LinkedList<>();

    protected BlockRetrievableRequest(DataStreamProcessor _io, SystemConnection _conn, JsonObject _reqObj) {
        super(_io, _conn, _reqObj);
        m_isTerseData = getRequestFieldBoolean("terse", false);
    }

    List<Object> getNextDataBlock(final int _numRows) throws SQLException {
        if (m_isDone) {
            return new LinkedList<Object>();
        }
        DataBlockFetchResult result = getNextDataBlock(m_rs, _numRows, m_isTerseData, getSystemConnection());
        m_isDone = result.isDone();
        // Stash deferred close info so processAfterReplySent() can close it safely.
        if (result.m_deferredRs != null) {
            m_deferredFetchResult = result;
        }
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
        SystemConnection conn = getSystemConnection();
        for (int i = 1; i <= numParams; ++i) {
            Map<String, Object> parmInfo = new LinkedHashMap<String, Object>();
            parmInfo.put("index", i);
            parmInfo.put("type", parmMeta.getParameterTypeName(i));
            parmInfo.put("precision", parmMeta.getPrecision(i));
            parmInfo.put("scale", parmMeta.getScale(i));
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
                } else if (value instanceof Blob) {
                    Blob blob = (Blob) value;
                    if (MapepireServer.isSingleMode()) {
                        try {
                            byte[] bytes = readAllBytes(blob.getBinaryStream());
                            blob.free();
                            jsonValue = Base64.getEncoder().encodeToString(bytes);
                        } catch (Exception e) {
                            Tracer.err(e);
                            jsonValue = null;
                        }
                    } else {
                        // Capture length before serializeBlob — for small blobs,
                        // storeAndReturnEntry calls blob.free(), so blob.length()
                        // after that throws HY010.
                        long blobLength = blob.length();
                        BlobStore.BlobEntry bentry = serializeBlob(blob, blobLength, conn,
                                m_pendingOutputParamSpools);
                        jsonValue = bentry != null ? blobEntryToRef(bentry, blobLength) : null;
                    }
                } else if (value instanceof Clob) {
                    Clob clob = (Clob) value;
                    jsonValue = clob.getSubString(1, (int) clob.length());
                    clob.free();
                } else if (value instanceof byte[]) {
                    jsonValue = serializeBytes((byte[]) value, conn);
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

        /** ResultSet (and optional statement) to close after spool streams open. */
        ResultSet m_deferredRs = null;
        boolean   m_deferredCloseStatement = false;

        /** BlobEntries from async spools — awaited via awaitReady() before closing RS. */
        final List<BlobStore.BlobEntry> m_pendingSpools = new LinkedList<>();

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

        /**
         * Wait for all async spool threads to finish writing to disk, then close
         * the deferred ResultSet/Statement. Safe to call multiple times.
         *
         * <p>We must wait for the full spool (not just stream-open) because the
         * AS400 JDBC driver may close the blob's {@link InputStream} when the
         * {@link ResultSet} or {@link Statement} is closed, cutting off a
         * mid-read spool thread.</p>
         */
        void closeDeferredResultSet() {
            if (m_deferredRs == null) return;
            // Wait for every spool thread to finish writing before closing the RS.
            for (BlobStore.BlobEntry e : m_pendingSpools) {
                try { e.awaitReady(); } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            try {
                if (m_deferredCloseStatement) {
                    m_deferredRs.getStatement().close();
                } else {
                    m_deferredRs.close();
                }
            } catch (SQLException ignored) {}
            m_deferredRs = null;
        }
    }

    protected static DataBlockFetchResult getNextDataBlock(final ResultSet _rs, final int _numRows,
                                                           final boolean _isTerseDataFormat,
                                                           final SystemConnection _conn) throws SQLException {
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
                // Defer the RS close only when there are pending async spool threads that
                // still need the JDBC Blob stream open. For small blobs (no async spool)
                // close immediately as before to avoid HY010 "Function sequence" errors
                // if the client sends the next request before processAfterReplySent() runs.
                // The deferred close is resolved in processAfterReplySent() after the WS
                // reply is sent, by which point all spool threads have opened their streams.
                if (!ret.m_pendingSpools.isEmpty()) {
                    ret.m_deferredRs = _rs;
                    ret.m_deferredCloseStatement = !(_rs.getStatement() instanceof PreparedStatement);
                } else {
                    // No pending spools — safe to close now
                    if (_rs.getStatement() instanceof PreparedStatement) {
                        _rs.close();
                    } else {
                        _rs.getStatement().close();
                    }
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
                    cellDataForResponse = cellData.toString();
                    int columnType = _rs.getMetaData().getColumnType(col);
                    if (columnType == Types.CHAR || columnType == Types.NCHAR) {
                        cellDataForResponse = ((String) cellDataForResponse).replaceAll("\\s+$", "");
                    }
                } else if (cellData instanceof Number || cellData instanceof Boolean) {
                    cellDataForResponse = cellData;
                } else if (cellData instanceof Blob) {
                    Blob blob = (Blob) cellData;
                    if (MapepireServer.isSingleMode()) {
                        // Single mode — no HTTP server, fall back to inline Base64
                        try {
                            byte[] bytes = readAllBytes(blob.getBinaryStream());
                            blob.free();
                            cellDataForResponse = Base64.getEncoder().encodeToString(bytes);
                        } catch (Exception e) {
                            Tracer.err(e);
                            cellDataForResponse = null;
                        }
                    } else {
                        // Capture length before serializeBlob, which calls blob.free()
                        // for small blobs — calling blob.length() after free() throws HY010.
                        long blobLength = blob.length();
                        BlobStore.BlobEntry entry = serializeBlob(blob, blobLength, _conn, ret.m_pendingSpools);
                        cellDataForResponse = entry != null ? blobEntryToRef(entry, blobLength) : null;
                    }
                } else if (cellData instanceof Clob) {
                    Clob clob = (Clob) cellData;
                    cellDataForResponse = clob.getSubString(1, (int) clob.length());
                    clob.free();
                } else if (cellData instanceof byte[]) {
                    cellDataForResponse = serializeBytes((byte[]) cellData, _conn);
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

    // -------------------------------------------------------------------------
    // BLOB serialization helpers
    // -------------------------------------------------------------------------

    /**
     * Daemon-mode only: stores blob in {@link BlobStore} and tracks any async spool
     * entry in the supplied list so the caller can defer closing the JDBC resource.
     *
     * <p>Accepts either a {@link DataBlockFetchResult#m_pendingSpools} list (result-set
     * path) or {@link #m_pendingOutputParamSpools} (output-parameter path).</p>
     */
    private static BlobStore.BlobEntry serializeBlob(Blob blob, long length,
                                                      SystemConnection conn,
                                                      List<BlobStore.BlobEntry> pendingSpools) {
        try {
            // BlobStore.storeAndReturnEntry owns blob.free() from this point on
            BlobStore.BlobEntry entry = BlobStore.getInstance().storeAndReturnEntry(blob, length, conn.getRawCredentials());
            // Only track entries that have a live async spool thread — small blobs
            // are stored synchronously and their latch is already at 0.
            if (entry != null && entry.isAsyncSpool()) {
                pendingSpools.add(entry);
            }
            return entry;
        } catch (IOException e) {
            Tracer.err(e);
            try { blob.free(); } catch (Exception ignored) {}
            return null;
        }
    }

    private static Map<String, Object> blobEntryToRef(BlobStore.BlobEntry entry, long length) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("blob_url", "/blob/" + entry.getToken());
        ref.put("size", length);
        return ref;
    }

    private static Object serializeBytes(byte[] bytes, SystemConnection conn) {
        if (MapepireServer.isSingleMode()) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        try {
            String token = BlobStore.getInstance().store(bytes, conn.getRawCredentials());
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("blob_url", "/blob/" + token);
            ref.put("size", (long) bytes.length);
            return ref;
        } catch (IOException e) {
            Tracer.err(e);
            return null;
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[65536];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buf.write(chunk, 0, read);
        }
        return buf.toByteArray();
    }

    public boolean isDone() {
        return m_isDone;
    }

    /**
     * Called by {@link com.github.ibm.mapepire.ClientRequest#run()} after the WebSocket
     * reply has been sent. Closes any ResultSet that was deferred to allow async blob
     * spool threads to finish writing, and waits for any output-parameter async spools.
     */
    @Override
    protected void processAfterReplySent() {
        if (m_deferredFetchResult != null) {
            m_deferredFetchResult.closeDeferredResultSet();
            m_deferredFetchResult = null;
        }
        // Drain any async output-parameter blob spools.
        for (BlobStore.BlobEntry e : m_pendingOutputParamSpools) {
            try { e.awaitReady(); } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        m_pendingOutputParamSpools.clear();
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
            columnAttrs.put("autoIncrement", _md.isAutoIncrement(i));
            columnAttrs.put("nullable", _md.isNullable(i));
            columnAttrs.put("readOnly", _md.isReadOnly(i));
            columnAttrs.put("writeable", _md.isWritable(i));
            columnAttrs.put("table", _md.getTableName(i));
            // Signal to clients that this column's value will be a blob_url object
            // in daemon mode rather than an inline string value.
            int colType = _md.getColumnType(i);
            boolean isBlobType = colType == Types.BLOB || colType == Types.BINARY
                    || colType == Types.VARBINARY || colType == Types.LONGVARBINARY;
            columnAttrs.put("blob_as_url", isBlobType && !MapepireServer.isSingleMode());
            columnMetaData.add(columnAttrs);
        }
        metaData.put("columns", columnMetaData);
        return metaData;
    }

}
