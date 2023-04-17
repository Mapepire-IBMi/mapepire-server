package com.github.theprez.codefori.requests;

import java.util.concurrent.atomic.AtomicInteger;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.github.theprez.codefori.Version;
import com.google.gson.JsonObject;

public class UnparsableReq extends ClientRequest {

    private String m_str;
    private static AtomicInteger m_int = new AtomicInteger(1);

    public UnparsableReq(DataStreamProcessor _io, SystemConnection m_conn, String _str) {
        super(_io, m_conn, getUnparseableJsonObject());
        m_str = _str;
    }

    private static JsonObject getUnparseableJsonObject() {
        JsonObject ret = new JsonObject();
        ret.addProperty("id", "unparseable_"+m_int.getAndIncrement());
        return ret;
    }

    @Override
    protected void go() throws Exception {
        addReplyData("unparseable", m_str);
        throw new RuntimeException("Non-parseable request: '" + m_str + "'");
    }
}
