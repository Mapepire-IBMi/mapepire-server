package com.github.ibm.mapepire.requests;

import java.sql.Connection;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
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
