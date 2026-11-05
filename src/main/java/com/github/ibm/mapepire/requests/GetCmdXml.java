package com.github.ibm.mapepire.requests;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.google.gson.JsonObject;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.Command;

/**
 * Handles a {@code getcmdxml} request: returns the extended XML command definition for an IBM i {@code *CMD}
 * object, as returned by the {@code QCDRCMDD} API in {@code CMDD0200} format. This is the raw command
 * definition, not generated HTML or UIM help documentation.
 */
public class GetCmdXml extends ClientRequest {

    public GetCmdXml(final DataStreamProcessor _io, final SystemConnection _conn, final JsonObject _reqObj) {
        super(_io, _conn, _reqObj);
    }

    /**
     * Retrieve the extended command definition XML for the command identified by the request's {@code path}
     * field and reply with it unchanged in the {@code xml} reply field.
     *
     * @throws IllegalStateException
     *             if there is no IBM i system connection
     * @throws Exception
     *             if the path is missing or invalid, or the command definition cannot be retrieved
     */
    @Override
    protected void go() throws Exception {
        final String path = getRequestField("path").getAsString();
        final AS400 system = getSystemConnection().getSystem();
        if (null == system) {
            throw new IllegalStateException("Not connected to an IBM i system.");
        }
        final Command command = new Command(system, path);
        addReplyData("xml", command.getXMLExtended());
    }
}
