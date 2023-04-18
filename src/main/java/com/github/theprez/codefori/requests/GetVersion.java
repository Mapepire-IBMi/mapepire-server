package com.github.theprez.codefori.requests;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.github.theprez.codefori.Version;
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
