package com.github.theprez.codefori.requests;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.google.gson.JsonObject;

public class Exit extends ClientRequest {

    public Exit(DataStreamProcessor _io, SystemConnection m_conn, JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    @Override
    protected void go() throws Exception {
    }

    @Override
    protected void processAfterReplySent() {
        System.exit(0);
    }
}
