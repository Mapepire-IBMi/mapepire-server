package com.github.ibm.mapepire.requests;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.github.ibm.mapepire.Tracer.Dest;
import com.github.ibm.mapepire.Tracer.TraceLevel;
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
                getConnection().getTracer().setDest(newDest);
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
                getConnection().getTracer().setTraceLevel(newLevel);
            }
        }
        addReplyData("tracedest", getConnection().getTracer().getDestString());
        addReplyData("tracelevel", "" + getConnection().getTracer().getTraceLevel().name());

    }

}
