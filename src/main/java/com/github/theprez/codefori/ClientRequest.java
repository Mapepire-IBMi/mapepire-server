package com.github.theprez.codefori;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ibm.as400.access.AS400;

public abstract class ClientRequest implements Runnable {
    private final Map<String, Object> replyData = new LinkedHashMap<String, Object>();
    private final DataStreamProcessor m_io;
    private JsonObject m_reqObj;
    private final String m_id;
    private SystemConnection m_conn;

    protected ClientRequest(DataStreamProcessor _io, SystemConnection _conn, JsonObject _reqObj) {
        m_io = _io;
        m_reqObj = _reqObj;
        m_id = _reqObj.get("id").getAsString();
        m_conn = _conn;
        addReplyData("id", m_id);
    }

    protected void addReplyData(String _key, Object _val) {
        replyData.put(_key, _val);
    }

    protected JsonElement getRequestField(String _key) {
        return m_reqObj.get(_key);
    }

    protected void sendreply() throws UnsupportedEncodingException, IOException {
        Gson l = new Gson();
        String json = l.toJson(replyData);
        m_io.sendResponse(json);
    }

    protected abstract void go() throws Exception;

    @Override
    public void run() {
        try {
            go();
            addReplyData("success", true);
        } catch (Exception _e) {
            _e.printStackTrace();
            addReplyData("success", false);
            addReplyData("error", _e.getLocalizedMessage());
        } finally {
            try {
                sendreply();
                processAfterReplySent();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

    protected void processAfterReplySent() {
        
    }

    public int getRequestFieldInt(String _key, int _default) {
        JsonElement j = getRequestField(_key);
        try {
            return j.getAsInt();
        } catch (Exception e) {
        }
        return _default;
    }

    public String getId() {
        return m_id;
    }

    public SystemConnection getSystemConnection() {
        return m_conn;
    }
    public boolean isForcedSynchronous() {
        return false;
    }

}
