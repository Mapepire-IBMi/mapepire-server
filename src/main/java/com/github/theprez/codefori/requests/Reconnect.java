package com.github.theprez.codefori.requests;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.github.theprez.codefori.Tracer;
import com.github.theprez.codefori.SystemConnection.ConnectionOptions;
import com.github.theprez.codefori.SystemConnection.ConnectionOptions.ConnectionMethod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class Reconnect extends ClientRequest {

    public Reconnect(final DataStreamProcessor _io, final SystemConnection _conn, final JsonObject _reqObj) {
        super(_io, _conn, _reqObj);
    }

    @Override
    protected void go() throws Exception {
        final JsonElement props = getRequestField("props");
        final JsonElement connectionType = getRequestField("technique");
        final ConnectionOptions opts = new ConnectionOptions();

        if (null != connectionType) {
            try {
                ConnectionMethod technique = ConnectionMethod.valueOf(connectionType.getAsString().toUpperCase());
                opts.setConnectionMethod(technique);
            } catch (Exception e) {
                throw new RuntimeException("Invalid connection technique specified");
            }
        }
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
