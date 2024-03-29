package com.github.theprez.codefori.requests;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.github.theprez.codefori.Tracer;
import com.github.theprez.codefori.ws.DbSocketCreator;
import com.google.gson.JsonObject;

public class Exit extends ClientRequest {

    public Exit(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    @Override
    protected void go() throws Exception {
        addReplyData("success", getSystemConnection().getJdbcJobName());
    }

    @Override
    protected void processAfterReplySent() {
        if (DbSocketCreator.isDaemon()) {
            this.getConnection().close();
        } else {
            Tracer.info("exiting as requested");
            System.exit(0);
        }
    }
}
