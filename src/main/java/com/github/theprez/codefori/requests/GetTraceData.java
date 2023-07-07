package com.github.theprez.codefori.requests;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.github.theprez.codefori.Tracer;
import com.github.theprez.codefori.Tracer.Dest;
import com.github.theprez.codefori.Tracer.TraceLevel;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ibm.as400.access.AS400JDBCConnection;

public class GetTraceData extends ClientRequest {

    public GetTraceData(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    @Override
    public void go() throws Exception {
        StringBuffer rawData = Tracer.get().getRawData();
        String l = rawData.toString();
        addReplyData("tracedata", rawData);
    }

}
