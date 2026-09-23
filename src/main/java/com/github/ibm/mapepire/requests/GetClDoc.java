package com.github.ibm.mapepire.requests;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.google.gson.JsonObject;
import com.ibm.as400.access.AS400JDBCConnection;
import com.ibm.as400.access.Command;
import com.ibm.as400.util.CommandHelpRetriever;

public class GetClDoc extends ClientRequest {

    public GetClDoc(final DataStreamProcessor _io, final SystemConnection _conn, final JsonObject _reqObj) {
        super(_io, _conn, _reqObj);
    }

    @Override
    protected void go() throws Exception {
        final String path = getRequestField("path").getAsString();
        final AS400JDBCConnection jdbcConn = (AS400JDBCConnection) getSystemConnection().getJdbcConnection();
        final Command command = new Command(jdbcConn.getSystem(), path);
        final CommandHelpRetriever helpRetriever = new CommandHelpRetriever();
        addReplyData("html", helpRetriever.generateHTML(command));
        addReplyData("uim", helpRetriever.generateUIM(command));
    }
}
