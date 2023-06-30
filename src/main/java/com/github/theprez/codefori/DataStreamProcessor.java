package com.github.theprez.codefori;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import com.github.theprez.codefori.requests.BlockRetrievableRequest;
import com.github.theprez.codefori.requests.Exit;
import com.github.theprez.codefori.requests.GetDbJob;
import com.github.theprez.codefori.requests.GetVersion;
import com.github.theprez.codefori.requests.IncompleteReq;
import com.github.theprez.codefori.requests.Ping;
import com.github.theprez.codefori.requests.PrepareSql;
import com.github.theprez.codefori.requests.PreparedExecute;
import com.github.theprez.codefori.requests.Reconnect;
import com.github.theprez.codefori.requests.RunSql;
import com.github.theprez.codefori.requests.RunSqlMore;
import com.github.theprez.codefori.requests.UnknownReq;
import com.github.theprez.codefori.requests.UnparsableReq;
import com.github.theprez.jcmdutils.StringUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class DataStreamProcessor implements Runnable {

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
            _req.run();
        } else {
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
                final JsonElement reqElement;
                final JsonObject reqObj;
                try {
                    reqElement = JsonParser.parseString(requestString);
                    reqObj = reqElement.getAsJsonObject();
                } catch (final Exception e) {
                    dispatch(new UnparsableReq(this, m_conn, requestString));
                    continue;
                }
                final JsonElement type = reqObj.get("type");
                if (null == type || null == reqObj.get("id")) {
                    dispatch(new IncompleteReq(this, m_conn, requestString));
                    continue;
                }
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
                    case "sqlmore":
                        BlockRetrievableRequest prev = m_queriesMap.get(reqObj.get("cont_id").getAsString());
                        if (null == prev) {
                            prev = m_prepStmtMap.get(reqObj.get("cont_id").getAsString());
                        }
                        dispatch(new RunSqlMore(this, reqObj, prev));
                        break;
                    case "execute":
                        final PrepareSql prevP = m_prepStmtMap.get(reqObj.get("cont_id").getAsString());
                        dispatch(new PreparedExecute(this, reqObj, prevP));
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
                    case "exit":
                        dispatch(new Exit(this, m_conn, reqObj));
                        break;
                    default:
                        dispatch(new UnknownReq(this, m_conn, reqObj, typeString));
                }
            }
        } catch (JsonSyntaxException | IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

    }

    public void sendResponse(final String _response) throws UnsupportedEncodingException, IOException {
        m_out.write((_response + "\n").getBytes("UTF-8"));
        m_out.flush();
    }

}
