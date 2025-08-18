package com.github.ibm.mapepire;

import com.github.ibm.mapepire.requests.*;
import com.github.ibm.mapepire.ws.BinarySender;
import com.github.ibm.mapepire.ws.DbWebsocketClient;
import com.github.theprez.jcmdutils.StringUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataStreamProcessor implements Runnable {

    private static final Object s_replyWriterLock = new String("Response Writer Lock");

    private final SystemConnection m_conn;
    private final BufferedReader m_in;
    private final PrintStream m_out_text;

    private final Map<String, RunSql> m_queriesMap = new HashMap<String, RunSql>();
    private final Map<String, PrepareSql> m_prepStmtMap = new HashMap<String, PrepareSql>();
    private final boolean m_isTestMode;
    private final BinarySender m_binarySender;
//    private final DbWebsocketClient.BinarySender m_binarySender;

    public DataStreamProcessor(final InputStream _in, final PrintStream _outText, final BinarySender binarySender, final SystemConnection _conn,
                               boolean _isTestMode)
            throws UnsupportedEncodingException {
        m_in = new BufferedReader(new InputStreamReader(_in, "UTF-8"));
        m_out_text = _outText;
        m_binarySender = binarySender;
        m_conn = _conn;
        m_isTestMode = _isTestMode;
    }

    private void dispatch(final ClientRequest _req) {
        dispatch(_req, false);
    }

    private void dispatch(final ClientRequest _req, final boolean _isForcedSynchronous) {
        if (m_isTestMode || _isForcedSynchronous || _req.isForcedSynchronous()) {
            Tracer.info("synchronously dispatcing a request of type: " + _req.getClass().getSimpleName());
            _req.run();
        } else {
            Tracer.info("asynchronously dispatcing a request of type: " + _req.getClass().getSimpleName());
            new Thread(_req).start();
        }
    }

    @Override
    public void run() {
        try {
            String requestString = null;
            while (null != (requestString = m_in.readLine())) {
                if (StringUtils.isEmpty(requestString)) {
                    continue;
                }
                Tracer.datastreamIn(requestString);
                if (requestString.startsWith("//")) {
                    continue;
                }

                run(requestString);
            }
        } catch (Exception e) {
            Tracer.err(e);
            System.exit(6);
        }
    }

    private int bytesToInt(byte[] bytes, int offset, int length) {
        int x = 0;
        for (int i = offset; i < offset + length; i++) {
            byte b = bytes[i];
            x = x << 8 | b & 0xFF;
        }
        return x;
    }


    public void run(byte[] payload, int offset, int len) {
        int cont_id = bytesToInt(payload, offset, 2);
        int length = bytesToInt(payload, offset + 2, 4);

        if (payload.length - 6 != length) {
            throw new RuntimeException("Invalid binary data recieved.");
        }


        PrepareSql prev = m_prepStmtMap.get(String.valueOf(cont_id));
        if (null == prev) {
            dispatch(new BadReq(this, m_conn, null, "invalid correlation ID"));
            return;
        }
//        byte[] blob = Arrays.copyOfRange(payload, 6, payload.length);
        int blobOffset = 6;
        try {
            RunBlob runBlob = new RunBlob(this, payload, blobOffset, length, prev);
        } catch (Exception e) {
            System.out.println("Caught exception " + e);
        }
    }


//    public void run(byte[] binary) {
//        int cont_id = bytesToInt(binary, 0, 2);
//        int length = bytesToInt(binary, 2, 4);
//
//        if (binary.length - 6 != length) {
//            throw new RuntimeException("Invalid binary data recieved.");
//        }
//
//
//        PrepareSql prev = m_prepStmtMap.get(String.valueOf(cont_id));
//        if (null == prev) {
//            dispatch(new BadReq(this, m_conn, null, "invalid correlation ID"));
//            return;
//        }
//        byte[] blob = Arrays.copyOfRange(binary, 6, binary.length);
//        try {
//            RunBlob runBlob = new RunBlob(this, blob, prev);
//        } catch (Exception e) {
//            System.out.println("Caught exception " + e);
//        }
//
//    }

    public void run(String requestString) {
        final JsonElement reqElement;
        final JsonObject reqObj;
        try {
            reqElement = JsonParser.parseString(requestString);
            reqObj = reqElement.getAsJsonObject();
        } catch (final Exception e) {
            dispatch(new UnparsableReq(this, m_conn, requestString));
            return;
        }
        final JsonElement type = reqObj.get("type");
        if (null == type || null == reqObj.get("id")) {
            dispatch(new IncompleteReq(this, m_conn, requestString));
            return;
        }
        JsonElement cont_id = reqObj.get("cont_id");
        final String typeString = type.getAsString();
        switch (typeString) {
            case "ping":
                dispatch(new Ping(this, m_conn, reqObj));
                break;
            case "sql":
                final RunSql runSqlReq = new RunSql(this, m_conn, reqObj);
                m_queriesMap.put(runSqlReq.getId(), runSqlReq);
                dispatch(runSqlReq);
                break;
            case "prepare_sql":
                final PrepareSql prepSqlReq = new PrepareSql(this, m_conn, reqObj, false);
                m_prepStmtMap.put(prepSqlReq.getId(), prepSqlReq);
                dispatch(prepSqlReq);
                break;
            case "prepare_sql_execute":
                final PrepareSql prepSqlExecReq = new PrepareSql(this, m_conn, reqObj, true);
                m_prepStmtMap.put(prepSqlExecReq.getId(), prepSqlExecReq);
                dispatch(prepSqlExecReq);
                break;
            case "sqlmore": {
                if (null == cont_id) {
                    dispatch(new BadReq(this, m_conn, reqObj, "Correlation ID not specified"));
                }
                BlockRetrievableRequest prev = m_queriesMap.get(cont_id.getAsString());
                if (null == prev) {
                    prev = m_prepStmtMap.get(cont_id.getAsString());
                }
                if (null == prev) {
                    dispatch(new BadReq(this, m_conn, reqObj, "invalid correlation ID"));
                    break;
                }
                dispatch(new RunSqlMore(this, reqObj, prev));
                break;
            }
            case "sqlclose": {
                if (null == cont_id) {
                    dispatch(new BadReq(this, m_conn, reqObj, "Correlation ID not specified"));
                }
                BlockRetrievableRequest prev = m_queriesMap.get(cont_id.getAsString());
                if (null == prev) {
                    prev = m_prepStmtMap.get(cont_id.getAsString());
                }
                if (null == prev) {
                    dispatch(new BadReq(this, m_conn, reqObj, "invalid correlation ID"));
                    break;
                }
                dispatch(new CloseSqlCursor(this, reqObj, prev));

                m_queriesMap.remove(cont_id.getAsString());
                m_prepStmtMap.remove(cont_id.getAsString());
                break;
            }
//            case "blob": {
//                final RunBlob blob = new RunBlob(this, m_conn, reqObj);
//                m_queriesMap.put(runSqlReq.getId(), runSqlReq);
//                dispatch(runSqlReq);
//                break;
//            }
            case "execute":
                if (null == cont_id) {
                    dispatch(new BadReq(this, m_conn, reqObj, "Correlation ID not specified"));
                    break;
                }
                final PrepareSql prevP = m_prepStmtMap.get(cont_id.getAsString());
                if (null == prevP) {
                    dispatch(new BadReq(this, m_conn, reqObj, "invalid correlation ID"));
                    break;
                }
                dispatch(new PreparedExecute(this, reqObj, prevP));
                break;
            case "cl":
                dispatch(new RunCL(this, m_conn, reqObj));
                break;
            case "dove":
                dispatch(new DoVe(this, m_conn, reqObj));
                break;
            case "connect":
                dispatch(new Reconnect(this, m_conn, reqObj));
                break;
            case "getdbjob":
                dispatch(new GetDbJob(this, m_conn, reqObj));
                break;
            case "getversion":
                dispatch(new GetVersion(this, m_conn, reqObj));
                break;
            case "setconfig":
                dispatch(new SetConfig(this, m_conn, reqObj));
                break;
            case "gettracedata":
                dispatch(new GetTraceData(this, m_conn, reqObj));
                break;
            case "exit":
                dispatch(new Exit(this, m_conn, reqObj));
                break;
            default:
                dispatch(new UnknownReq(this, m_conn, reqObj, typeString));
        }

    }

    public void sendResponse(final String _response) throws UnsupportedEncodingException, IOException {
        synchronized (s_replyWriterLock) {
            m_out_text.write((_response + "\n").getBytes("UTF-8"));
            Tracer.datastreamOut(_response);
            m_out_text.flush();
        }
    }

//    public void sendResponse(final InputStream is, final String id) throws UnsupportedEncodingException, IOException {
//        synchronized (s_replyWriterLock) {
//            byte[] buffer = new byte[8192];
//            int bytesRead;
//            boolean isFinal;
//            while ((bytesRead = is.read(buffer)) != -1) {
//                // Wrap only the bytes actually read
//                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead);
//
//                // Check if this is the last chunk
//                isFinal = is.available() == 0;
//
//                m_binarySender.send(byteBuffer, isFinal);
//            }
//        }
//    }

    public void sendResponse(final String id, final List<BlobResponseData> blobResponseDataArr) throws IOException {
        synchronized (s_replyWriterLock) {
            int curOffset = 0;
            byte[] buffer = new byte[8192];
            byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);

            for (int i = 0; i < blobResponseDataArr.size(); i++){
                buffer = new byte[8192];
                curOffset = 0;
                BlobResponseData blobResponseData = blobResponseDataArr.get(i);
                String columnName = blobResponseData.getColumnName();
                InputStream is = blobResponseData.getIs();
                int rowId = blobResponseData.getRowId();

                byte[] columnNameBytes = columnName.getBytes(StandardCharsets.UTF_8);

                if (idBytes.length > 255) {
                    throw new IllegalArgumentException("ID too long to encode in one byte length");
                }

                if (i == 0){
                    // First byte is the length of the ID
                    buffer[curOffset] = (byte) idBytes.length;
                    curOffset += 1;
                    // Copy ID bytes after the length byte
                    System.arraycopy(idBytes, 0, buffer, curOffset, idBytes.length);
                    curOffset += idBytes.length;
                }

                // Copy rowId
                buffer[curOffset] = (byte) rowId;
                curOffset += 1;


                // Copy column length
                buffer[curOffset] = (byte) columnName.length();
                curOffset += 1;

                // copy column name
                System.arraycopy(columnNameBytes, 0, buffer, curOffset, columnNameBytes.length);
                curOffset += columnNameBytes.length;

                // Copy blob length
                ByteBuffer blobLength = ByteBuffer.allocate(4);
                blobLength.putInt(is.available()); // default is big-endian
                byte[] bytes = blobLength.array();
                for (int j = 0; j < 4; j++){
                    buffer[curOffset] = bytes[j];
                    curOffset += 1;
                }

                int bytesRead = is.read(buffer, curOffset, buffer.length - curOffset);
                curOffset += bytesRead;
                if (bytesRead != -1) {
                    boolean isFinal = i == blobResponseDataArr.size() - 1 && is.available() == 0;
                    sendByteBuffer(buffer, curOffset, isFinal);
                }

                while ((bytesRead = is.read(buffer)) != -1) {
                    boolean isFinal = i == blobResponseDataArr.size() - 1 && is.available() == 0;
                    sendByteBuffer(buffer, bytesRead, isFinal);
                }
            }

        }
    }

    private void sendByteBuffer(byte[] buffer, int bytesRead, boolean isFinal) throws IOException {
        // Wrap only the bytes actually read
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead);

        m_binarySender.send(byteBuffer, isFinal);
    }

    public void end() {
        try {
            m_conn.getJdbcConnection().close();
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
