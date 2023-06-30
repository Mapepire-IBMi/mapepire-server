package com.github.theprez.codefori.requests;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.google.gson.JsonObject;

public class RunSqlMore extends ClientRequest {

    private final BlockRetrievableRequest m_prev;

    public RunSqlMore(final DataStreamProcessor _io, final JsonObject _reqObj, final BlockRetrievableRequest _prev) {
        super(_io, _prev.getSystemConnection(), _reqObj);
        m_prev = _prev;
    }

    @Override
    protected void go() throws Exception {
        final int numRows = super.getRequestFieldInt("rows", 1000);
        addReplyData("data", m_prev.getNextDataBlock(numRows));
        addReplyData("is_done", m_prev.isDone());
    }

}
