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
            JsonElement traceDestFld = getRequestField("tracedest");
            if (null != traceDestFld) {
                String traceDest = traceDestFld.getAsString();
                Dest newDest = Dest.valueOf(traceDest.trim().toUpperCase());
                if (null == newDest) {
                    throw new RuntimeException("Invalid trace destination specified: " + traceDest);
                }
                Tracer.get().setDest(newDest);
            }
        }
        {
            JsonElement traceLevelFld = getRequestField("tracelevel");
            if (null != traceLevelFld) {
                String traceLevel = traceLevelFld.getAsString();
                TraceLevel newLevel = TraceLevel.valueOf(traceLevel.trim().toUpperCase());
                if (null == newLevel) {
                    throw new RuntimeException("Invalid trace level specified: "+traceLevel);
                }
                Tracer.get().setTraceLevel(newLevel);
            }
        }
        {
            JsonElement jtOpenTraceDestFld = getRequestField("jtopentracedest");
            if (null != jtOpenTraceDestFld) {
                String traceDest = jtOpenTraceDestFld.getAsString();
                Dest newDest = Dest.valueOf(traceDest.trim().toUpperCase());
                if (null == newDest) {
                    throw new RuntimeException("Invalid trace destination specified: " + traceDest);
                }
                Tracer.get().setJtOpenDest(newDest);
            }
        }
        {
            JsonElement jtopenTraceLevelFld = getRequestField("jtopentracelevel");
            if (null != jtopenTraceLevelFld) {
                String traceLevel = jtopenTraceLevelFld.getAsString();
                TraceLevel newLevel = TraceLevel.valueOf(traceLevel.trim().toUpperCase());
                if (null == newLevel) {
                    throw new RuntimeException("Invalid jtopen trace level specified: "+traceLevel);
                }
                Tracer.get().setJtOpenTraceLevel(newLevel);
            }
        }
        addReplyData("tracedest", Tracer.get().getDestString());
        addReplyData("tracelevel", "" + Tracer.get().getTraceLevel().name());
        addReplyData("jtopentracedest", Tracer.get().getJtOpenDestString());
        addReplyData("jtopentracelevel", "" + Tracer.get().getJtOpenTraceLevel().name());

    }

}
