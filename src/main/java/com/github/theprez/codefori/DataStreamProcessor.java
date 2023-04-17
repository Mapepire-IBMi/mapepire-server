package com.github.theprez.codefori;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import com.github.theprez.codefori.requests.Exit;
import com.github.theprez.codefori.requests.GetDbJob;
import com.github.theprez.codefori.requests.GetVersion;
import com.github.theprez.codefori.requests.IncompleteReq;
import com.github.theprez.codefori.requests.Ping;
import com.github.theprez.codefori.requests.Reconnect;
import com.github.theprez.codefori.requests.RunSql;
import com.github.theprez.codefori.requests.RunSqlMore;
import com.github.theprez.codefori.requests.UnknownReq;
import com.github.theprez.codefori.requests.UnparsableReq;
import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.ConsoleQuestionAsker;
import com.github.theprez.jcmdutils.StringUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400SecurityException;

public class DataStreamProcessor implements Runnable {

    private BufferedReader m_in;
    private PrintStream m_out;
    private AppLogger m_logger;
    private Map<String, RunSql> m_queriesMap = new HashMap<String, RunSql>();
    private SystemConnection m_conn;

    public DataStreamProcessor(AppLogger _logger, InputStream _in, PrintStream _out, SystemConnection _conn)
            throws UnsupportedEncodingException {
        m_in = new BufferedReader(new InputStreamReader(_in, "UTF-8"));
        m_out = _out;
        m_logger = _logger;
        m_conn = _conn;
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
                } catch (Exception e) {
                    dispatch(new UnparsableReq(this, m_conn, requestString));
                    continue;
                }
                final JsonElement type = reqObj.get("type");
                if (null == type || null == reqObj.get("id")) {
                    dispatch(new IncompleteReq(this, m_conn, requestString));
                    continue;
                }
                String typeString = type.getAsString();
                switch (typeString) {
                    case "ping":
                        dispatch(new Ping(this, m_conn, reqObj));
                        break;
                    case "sql":
                        RunSql runSqlReq = new RunSql(this, m_conn, reqObj);
                        m_queriesMap.put(runSqlReq.getId(), runSqlReq);
                        dispatch(runSqlReq);
                        break;
                    case "sqlmore":
                        final RunSql prev = m_queriesMap.get(reqObj.get("cont_id").getAsString());
                        dispatch(new RunSqlMore(this, reqObj, prev));
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

    private void dispatch(ClientRequest _req) {
        if (_req.isForcedSynchronous()) {
            _req.run();
        } else {
            new Thread(_req).start();
        }
    }

    public void sendResponse(String _response) throws UnsupportedEncodingException, IOException {
        m_out.write((_response + "\n").getBytes("UTF-8"));
        m_out.flush();
    }

}
