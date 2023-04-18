package com.github.theprez.codefori.requests;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.google.gson.JsonObject;

public class RunSqlMore extends ClientRequest {

    private final RunSql m_prev;

    public RunSqlMore(DataStreamProcessor _io, JsonObject _reqObj, RunSql _prev) {
        super(_io, _prev.getSystemConnection(), _reqObj);
        m_prev = _prev;
    }

    @Override
    protected void go() throws Exception {
        int numRows = super.getRequestFieldInt("rows", 1000);
        addReplyData("data", m_prev.getNextDataBlock(numRows));
        addReplyData("is_done", m_prev.isDone());
    }

}
