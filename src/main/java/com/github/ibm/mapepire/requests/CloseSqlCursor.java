package com.github.ibm.mapepire.requests;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.google.gson.JsonObject;

public class CloseSqlCursor extends ClientRequest {

    private final BlockRetrievableRequest m_prev;

    public CloseSqlCursor(final DataStreamProcessor _io, final JsonObject _reqObj, final BlockRetrievableRequest _prev) {
        super(_io, _prev.getSystemConnection(), _reqObj);
        m_prev = _prev;
    }

    @Override
    protected void go() throws Exception {
        m_prev.close();
    }

}
