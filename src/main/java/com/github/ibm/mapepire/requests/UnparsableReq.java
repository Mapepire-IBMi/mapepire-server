package com.github.ibm.mapepire.requests;

import java.util.concurrent.atomic.AtomicInteger;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.google.gson.JsonObject;

public class UnparsableReq extends ClientRequest {

    private static AtomicInteger m_int = new AtomicInteger(1);

    private static JsonObject getUnparseableJsonObject() {
        final JsonObject ret = new JsonObject();
        ret.addProperty("id", "unparseable_" + m_int.getAndIncrement());
        return ret;
    }

    private final String m_str;

    public UnparsableReq(final DataStreamProcessor _io, final SystemConnection m_conn, final String _str) {
        super(_io, m_conn, getUnparseableJsonObject());
        m_str = _str;
    }

    @Override
    protected void go() throws Exception {
        addReplyData("unparseable", m_str);
        throw new RuntimeException("Non-parseable request: '" + m_str + "'");
    }
}
