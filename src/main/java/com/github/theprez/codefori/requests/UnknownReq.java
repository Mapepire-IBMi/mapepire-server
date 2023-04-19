package com.github.theprez.codefori.requests;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.google.gson.JsonObject;

public class UnknownReq extends ClientRequest {

    private final String m_reqType;

    public UnknownReq(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj, final String _reqType) {
        super(_io, m_conn, _reqObj);
        m_reqType = _reqType;
    }

    @Override
    protected void go() throws Exception {
        throw new RuntimeException("Unknown request type: " + m_reqType);
    }
}
