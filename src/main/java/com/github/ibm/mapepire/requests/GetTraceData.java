package com.github.ibm.mapepire.requests;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.google.gson.JsonObject;

public class GetTraceData extends ClientRequest {

    public GetTraceData(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    @Override
    public void go() throws Exception {
        StringBuffer rawData = getConnection().getTracer().getRawData();
        addReplyData("tracedata", rawData);
    }
    @Override
    public boolean isForcedSynchronous() {
        return true;
    }
}