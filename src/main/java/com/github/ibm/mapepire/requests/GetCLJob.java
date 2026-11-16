package com.github.ibm.mapepire.requests;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.google.gson.JsonObject;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.Job;

import java.util.Optional;

public class GetCLJob extends ClientRequest {

    public GetCLJob(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    @Override
    protected void go() throws Exception {
        final String commandJobIdentifier = Optional.ofNullable(getSystemConnection().getSystem())
                        .map(system -> system.getJobs(AS400.COMMAND))
                .map(jobs -> jobs.length > 0 ? jobs[0] : null)
                .map(job -> String.format("%s/%s/%s", job.getNumber(), job.getUser(), job.getName()))
                .orElse("");
        addReplyData("job", commandJobIdentifier);
    }
}
