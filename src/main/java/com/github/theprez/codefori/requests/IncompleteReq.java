package com.github.theprez.codefori.requests;

import java.util.concurrent.atomic.AtomicInteger;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.github.theprez.codefori.Version;
import com.google.gson.JsonObject;

public class IncompleteReq extends ClientRequest {

    private String m_str;
    private static AtomicInteger m_int = new AtomicInteger(1);

    public IncompleteReq(DataStreamProcessor _io, SystemConnection m_conn, String _str) {
        super(_io, m_conn, getIncompleteJsonObject());
        m_str = _str;
    }

    private static JsonObject getIncompleteJsonObject() {
        JsonObject ret = new JsonObject();
        ret.addProperty("id", "incomplete_"+m_int.getAndIncrement());
        return ret;
    }

    @Override
    protected void go() throws Exception {
        addReplyData("incomplete", m_str);
        throw new RuntimeException("Request is missing required fields (most likely 'id' or 'type'): '" + m_str + "'");
    }
}
