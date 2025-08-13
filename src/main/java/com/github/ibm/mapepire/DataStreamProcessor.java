package com.github.ibm.mapepire;

import com.github.ibm.mapepire.requests.*;
import com.github.theprez.jcmdutils.StringUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DataStreamProcessor implements Runnable {

    private static final Object s_replyWriterLock = new String("Response Writer Lock");

    private final SystemConnection m_conn;
    private final BufferedReader m_in;
    private final PrintStream m_out;
    private final Map<String, RunSql> m_queriesMap = new HashMap<String, RunSql>();
    private final Map<String, PrepareSql> m_prepStmtMap = new HashMap<String, PrepareSql>();
    private final boolean m_isTestMode;

    public DataStreamProcessor(final InputStream _in, final PrintStream _out, final SystemConnection _conn,
                               boolean _isTestMode)
            throws UnsupportedEncodingException {
        m_in = new BufferedReader(new InputStreamReader(_in, "UTF-8"));
        m_out = _out;
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
            m_out.write((_response + "\n").getBytes("UTF-8"));
            Tracer.datastreamOut(_response);
            m_out.flush();
        }
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
