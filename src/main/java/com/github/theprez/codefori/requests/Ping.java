package com.github.theprez.codefori.requests;

import java.sql.Connection;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.google.gson.JsonObject;

public class Ping extends ClientRequest {

    public Ping(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    @Override
    public void go() throws Exception {
        addReplyData("alive", true);
        final Connection dbConn =  getSystemConnection().getJdbcConnection();
        addReplyData("db_alive",null != dbConn && !dbConn.isClosed());
    }

}
