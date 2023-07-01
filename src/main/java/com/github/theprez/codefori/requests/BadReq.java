package com.github.theprez.codefori.requests;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.google.gson.JsonObject;

public class BadReq extends ClientRequest {

    private final String m_error;

    public BadReq(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj,
            final String _err) {
        super(_io, m_conn, _reqObj);
        m_error = _err;
    }

    @Override
    protected void go() throws Exception {
        throw new RuntimeException("Bad request: " + m_error);
    }
}
