package com.github.ibm.mapepire.requests;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.github.ibm.mapepire.Version;
import com.google.gson.JsonObject;

public class GetVersion extends ClientRequest {

    public GetVersion(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    @Override
    protected void go() throws Exception {
        addReplyData("build_date", Version.s_compileDateTime);
        addReplyData("version", Version.s_version);
    }
}
