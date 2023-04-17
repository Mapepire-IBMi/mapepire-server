package com.github.theprez.codefori.requests;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.github.theprez.codefori.SystemConnection.ConnectionOptions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class Reconnect extends ClientRequest {

    public Reconnect(DataStreamProcessor _io, SystemConnection _conn, JsonObject _reqObj) {
        super(_io, _conn, _reqObj);
    }

    @Override
    protected void go() throws Exception {
        JsonElement props = getRequestField("props");
        ConnectionOptions opts = new ConnectionOptions();
        if (null != props) {
            opts.setJdbcProperties(props.getAsString());
        }
        getSystemConnection().reconnect(opts);
        addReplyData("job", getSystemConnection().getJdbcJobName());
    }

    @Override
    public boolean isForcedSynchronous() {
        return true;
    }

}
