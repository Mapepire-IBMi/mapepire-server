package com.github.theprez.codefori;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.github.theprez.jcmdutils.StringUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public abstract class ClientRequest implements Runnable {
    private final SystemConnection m_conn;
    private final String m_id;
    private final DataStreamProcessor m_io;
    private final JsonObject m_reqObj;
    private final Map<String, Object> replyData = new LinkedHashMap<String, Object>();

    protected ClientRequest(final DataStreamProcessor _io, final SystemConnection _conn, final JsonObject _reqObj) {
        m_io = _io;
        m_reqObj = _reqObj;
        m_id = _reqObj.get("id").getAsString();
        m_conn = _conn;
        addReplyData("id", m_id);
    }

    protected void addReplyData(final String _key, final Object _val) {
        replyData.put(_key, _val);
    }

    protected void mergeReplyData(final ClientRequest _secondary) {
        replyData.putAll(_secondary.replyData);
    }

    public String getId() {
        return m_id;
    }

    protected JsonElement getRequestField(final String _key) {
        return m_reqObj.get(_key);
    }

    public int getRequestFieldInt(final String _key, final int _default) {
        final JsonElement j = getRequestField(_key);
        try {
            return j.getAsInt();
        } catch (final Exception e) {
        }
        return _default;
    }

    public boolean getRequestFieldBoolean(String _key, boolean _default) {
        final JsonElement j = getRequestField(_key);
        try {
            return j.getAsBoolean();
        } catch (final Exception e) {
        }
        return _default;
    }

    public SystemConnection getSystemConnection() {
        return m_conn;
    }

    protected abstract void go() throws Exception;

    public boolean isForcedSynchronous() {
        return false;
    }

    protected void processAfterReplySent() {

    }

    @Override
    public void run() {
        try {
            go();
            addReplyData("success", true);
        } catch (final Exception _e) {
            Tracer.err(_e);
            addReplyData("error", "" + getErrorStringFromException(_e));
            addReplyData("success", false);
        } finally {
            try {
                sendreply();
                processAfterReplySent();
            } catch (final Exception e) {
                Tracer.err(e);
                System.exit(-1);
            }
        }
    }

    private static String getErrorStringFromException(Throwable _e) {
        if (null == _e) {
            return "crazy";
        }
        if (_e instanceof Error || _e instanceof NullPointerException) {
            return "Internal Error: " + Tracer.exceptionToStackTrace(_e);
        }
        String msg = _e.getLocalizedMessage();
        if (StringUtils.isNonEmpty(msg)) {
            return msg;
        }
        msg = _e.getMessage();
        if (StringUtils.isNonEmpty(msg)) {
            return msg;
        }
        return "Internal Error: " + _e.getClass().getSimpleName();
    }

    protected void sendreply() throws UnsupportedEncodingException, IOException {
        final Gson l = new GsonBuilder().serializeNulls().create();
        final String json = l.toJson(replyData);
        m_io.sendResponse(json);
    }
}
