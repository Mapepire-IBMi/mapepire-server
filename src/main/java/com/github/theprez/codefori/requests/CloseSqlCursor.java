package com.github.theprez.codefori.requests;

import java.sql.ResultSet;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.google.gson.JsonObject;

public class CloseSqlCursor extends ClientRequest {

    private final BlockRetrievableRequest m_prev;

    public CloseSqlCursor(final DataStreamProcessor _io, final JsonObject _reqObj, final BlockRetrievableRequest _prev) {
        super(_io, _prev.getSystemConnection(), _reqObj);
        m_prev = _prev;
    }

    @Override
    protected void go() throws Exception {
        ResultSet rs = m_prev.m_rs;
        if(null == rs) {
            return;
        }
        if(!rs.isClosed()) {
            rs.close();
        }
    }

}
