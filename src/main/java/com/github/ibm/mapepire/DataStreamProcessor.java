package com.github.ibm.mapepire;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import com.github.ibm.mapepire.requests.BadReq;
import com.github.ibm.mapepire.requests.BlockRetrievableRequest;
import com.github.ibm.mapepire.requests.CloseSqlCursor;
import com.github.ibm.mapepire.requests.DoVe;
import com.github.ibm.mapepire.requests.Exit;
import com.github.ibm.mapepire.requests.GetClDoc;
import com.github.ibm.mapepire.requests.GetCmdXml;
import com.github.ibm.mapepire.requests.GetDbJob;
import com.github.ibm.mapepire.requests.GetTraceData;
import com.github.ibm.mapepire.requests.GetVersion;
import com.github.ibm.mapepire.requests.IncompleteReq;
import com.github.ibm.mapepire.requests.Ping;
import com.github.ibm.mapepire.requests.PrepareSql;
import com.github.ibm.mapepire.requests.PreparedExecute;
import com.github.ibm.mapepire.requests.Reconnect;
import com.github.ibm.mapepire.requests.RunCL;
import com.github.ibm.mapepire.requests.RunSql;
import com.github.ibm.mapepire.requests.RunSqlCL;
import com.github.ibm.mapepire.requests.RunSqlMore;
import com.github.ibm.mapepire.requests.SetConfig;
import com.github.ibm.mapepire.requests.UnknownReq;
import com.github.ibm.mapepire.requests.UnparsableReq;
import com.github.theprez.jcmdutils.StringUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DataStreamProcessor implements Runnable {

    private static final Object s_replyWriterLock = new String("Response Writer Lock");

    private final SystemConnection m_conn;
    private final BufferedReader m_in;
    private final PrintStream m_out;
    private final Map<String, RunSql> m_queriesMap = new HashMap<String, RunSql>();
    private final Map<String, PrepareSql> m_prepStmtMap = new HashMap<String, PrepareSql>();
    private final boolean m_isTestMode;

    public DataStreamProcessor(final InputStream _in, final PrintStream _out, final SystemConnection _conn,
            final boolean _isTestMode)
            throws UnsupportedEncodingException {
        m_in = new BufferedReader(new InputStreamReader(_in, "UTF-8"));
        m_out = _out;
        m_conn = _conn;
        m_isTestMode = _isTestMode;
    }

    private void dispatch(final ClientRequest _req) {
        dispatch(_req, false);
    }

    /**
     * Run a request, either on the calling thread or on a new thread of its own.
     * <p>
     * The request runs synchronously when in test mode, when {@code _isForcedSynchronous} is set, or when the
     * request itself reports that it must run synchronously; otherwise it runs asynchronously.
     *
     * @param _req
     *            the request to run
     * @param _isForcedSynchronous
     *            whether the caller requires the request to run synchronously
     */
    private void dispatch(final ClientRequest _req, final boolean _isForcedSynchronous) {
        if (m_isTestMode || _isForcedSynchronous || _req.isForcedSynchronous()) {
            m_conn.getTracer().logInfo("synchronously dispatcing a request of type: " + _req.getClass().getSimpleName());
            _req.run();
        } else {
            m_conn.getTracer().logInfo("asynchronously dispatcing a request of type: " + _req.getClass().getSimpleName());
            new Thread(_req).start();
        }
    }

    /**
     * Read requests from the input stream, one per line, and process each until the stream ends. Empty lines
     * and lines beginning with {@code //} are skipped. Any failure while reading terminates the JVM.
     */
    @Override
    public void run() {
        try {
            String requestString = null;
            while (null != (requestString = m_in.readLine())) {
                if (StringUtils.isEmpty(requestString)) {
                    continue;
                }
                m_conn.getTracer().logDatastreamIn(requestString);
                if (requestString.startsWith("//")) {
                    continue;
                }

                run(requestString);
            }
        } catch (final Exception _e) {
            m_conn.getTracer().logErr(_e);
            System.exit(6);
        }
    }

    /**
     * Parse a single JSON request and dispatch it to the handler for its {@code type}. Requests that cannot be
     * parsed, that lack a {@code type} or {@code id}, or that refer to an unknown correlation ID are answered
     * with an error response rather than throwing.
     *
     * @param _requestString
     *            the raw JSON text of the request
     */
    public void run(final String _requestString) {
        final JsonElement reqElement;
        final JsonObject reqObj;
        try {
            reqElement = JsonParser.parseString(_requestString);
            reqObj = reqElement.getAsJsonObject();
        } catch (final Exception _e) {
            dispatch(new UnparsableReq(this, m_conn, _requestString));
            return;
        }
        final JsonElement type = reqObj.get("type");
        if (null == type || null == reqObj.get("id")) {
            dispatch(new IncompleteReq(this, m_conn, _requestString));
            return;
        }
        final JsonElement contId = reqObj.get("cont_id");
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
                if (null == contId) {
                    dispatch(new BadReq(this, m_conn, reqObj, "Correlation ID not specified"));
                    break;
                }
                BlockRetrievableRequest prev = m_queriesMap.get(contId.getAsString());
                if (null == prev) {
                    prev = m_prepStmtMap.get(contId.getAsString());
                }
                if (null == prev) {
                    dispatch(new BadReq(this, m_conn, reqObj, "invalid correlation ID"));
                    break;
                }
                dispatch(new RunSqlMore(this, reqObj, prev));
                break;
            }
            case "sqlclose": {
                if (null == contId) {
                    dispatch(new BadReq(this, m_conn, reqObj, "Correlation ID not specified"));
                    break;
                }
                BlockRetrievableRequest prev = m_queriesMap.get(contId.getAsString());
                if (null == prev) {
                    prev = m_prepStmtMap.get(contId.getAsString());
                }
                if (null == prev) {
                    dispatch(new BadReq(this, m_conn, reqObj, "invalid correlation ID"));
                    break;
                }
                dispatch(new CloseSqlCursor(this, reqObj, prev));

                m_queriesMap.remove(contId.getAsString());
                m_prepStmtMap.remove(contId.getAsString());
                break;
            }
            case "execute":
                if (null == contId) {
                    dispatch(new BadReq(this, m_conn, reqObj, "Correlation ID not specified"));
                    break;
                }
                final PrepareSql prevP = m_prepStmtMap.get(contId.getAsString());
                if (null == prevP) {
                    dispatch(new BadReq(this, m_conn, reqObj, "invalid correlation ID"));
                    break;
                }
                dispatch(new PreparedExecute(this, reqObj, prevP));
                break;
            case "rccl":
                dispatch(new RunCL(this, m_conn, reqObj));
                break;
            case "cl":
                dispatch(new RunSqlCL(this, m_conn, reqObj));
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
            case "getcldoc":
                dispatch(new GetClDoc(this, m_conn, reqObj));
                break;
            case "getcmdxml":
                dispatch(new GetCmdXml(this, m_conn, reqObj));
                break;
            case "exit":
                dispatch(new Exit(this, m_conn, reqObj));
                break;
            default:
                dispatch(new UnknownReq(this, m_conn, reqObj, typeString));
        }

    }

    /**
     * Write a single response to the output stream as one UTF-8 line, and trace it. Writes are serialized so
     * that responses from concurrently running requests are never interleaved.
     *
     * @param _response
     *            the JSON text of the response
     * @throws UnsupportedEncodingException
     *             if UTF-8 is not supported by the JVM
     * @throws IOException
     *             if the response cannot be written
     */
    public void sendResponse(final String _response) throws UnsupportedEncodingException, IOException {
        synchronized (s_replyWriterLock) {
            m_out.write((_response + "\n").getBytes("UTF-8"));
            m_conn.getTracer().logDatastreamOut(_response);
            m_out.flush();
        }
    }

    public void end() {
        m_conn.close();
    }

}
