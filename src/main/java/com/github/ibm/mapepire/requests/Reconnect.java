package com.github.ibm.mapepire.requests;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.github.ibm.mapepire.SystemConnection.ConnectionOptions;
import com.github.ibm.mapepire.SystemConnection.ConnectionOptions.ConnectionMethod;
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
        final JsonElement applicationName = getRequestField("application");
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
        String appName = null;
        if (null != applicationName) {
            appName = applicationName.getAsString();
        }
        getSystemConnection().reconnect(opts, appName);
        addReplyData("job", getSystemConnection().getJdbcJobName());
    }

    @Override
    public boolean isForcedSynchronous() {
        return true;
    }

}
