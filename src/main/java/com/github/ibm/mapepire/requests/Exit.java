package com.github.ibm.mapepire.requests;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.github.ibm.mapepire.Tracer;
import com.github.ibm.mapepire.ws.DbSocketCreator;
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
