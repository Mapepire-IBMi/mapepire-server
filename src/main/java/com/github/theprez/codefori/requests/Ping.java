package com.github.theprez.codefori.requests;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.google.gson.JsonObject;
import com.ibm.as400.access.AS400;

public class Ping extends ClientRequest {

    public Ping(DataStreamProcessor _io, SystemConnection m_conn, JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    public void go() throws Exception {
        addReplyData("alive", true);
    }

}
