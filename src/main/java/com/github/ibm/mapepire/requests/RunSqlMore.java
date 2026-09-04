package com.github.ibm.mapepire.requests;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
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

    /**
     * Delegate to the previous request so that any deferred ResultSet close
     * or pending output-param async spool is resolved after the WebSocket reply
     * is sent — even though it is this RunSqlMore instance whose run() executes. (Fix 4)
     */
    @Override
    protected void processAfterReplySent() {
        m_prev.processAfterReplySent();
    }

}
