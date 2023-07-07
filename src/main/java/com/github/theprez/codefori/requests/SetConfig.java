package com.github.theprez.codefori.requests;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.github.theprez.codefori.Tracer;
import com.github.theprez.codefori.Tracer.Dest;
import com.github.theprez.codefori.Tracer.TraceLevel;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class SetConfig extends ClientRequest {

    public SetConfig(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    @Override
    public void go() throws Exception {
        {
            JsonElement traceLevelFld = getRequestField("tracelevel");
            if (null != traceLevelFld) {
                String traceLevel = traceLevelFld.getAsString();
                TraceLevel newLevel = TraceLevel.valueOf(traceLevel.trim().toUpperCase());
                if (null == newLevel) {
                    throw new RuntimeException("Invalid trace level specified:");
                }
                Tracer.get().setTraceLevel(newLevel);
            }
        }

        JsonElement traceDestFld = getRequestField("tracedest");
        if (null != traceDestFld) {
            String traceDest = traceDestFld.getAsString();
            Dest newDest = Dest.valueOf(traceDest.trim().toUpperCase());
            if (null == newDest) {
                throw new RuntimeException("Invalid trace destination specified: " + traceDest);
            }
            Tracer.get().setDest(newDest);
        }
        addReplyData("tracedest", Tracer.get().getDestString());
        addReplyData("tracelevel", "" + Tracer.get().getTraceLevel().name());
    }

}
