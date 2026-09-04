package com.github.ibm.mapepire.requests;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.github.ibm.mapepire.SystemConnection.ConnectionMethod;
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
        
        ConnectionMethod technique = ConnectionMethod.getDefault();
        if (null != connectionType) {
            try {
                technique = ConnectionMethod.valueOf(connectionType.getAsString().toUpperCase());
            } catch (Exception e) {
                throw new RuntimeException("Invalid connection technique specified");
            }
        }
        
        String jdbcProps = null;
        if (null != props) {
            jdbcProps = props.getAsString();
        }
        
        String appName = null;
        if (null != applicationName) {
            appName = applicationName.getAsString();
        }
        
        getSystemConnection().reconnect(technique, jdbcProps, appName);
        addReplyData("job", getSystemConnection().getJdbcJobName());
    }

    @Override
    public boolean isForcedSynchronous() {
        return true;
    }

}
