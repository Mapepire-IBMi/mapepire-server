package com.github.ibm.mapepire.requests;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.google.gson.JsonObject;

public class RunCL extends BlockRetrievableRequest {

    private PreparedStatement m_stmt = null;

    public RunCL(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    @Override
    public boolean isForcedSynchronous() {
        return true;
    }
;
    @Override
    public void go() throws Exception {
        final String cmd = getRequestField("cmd").getAsString();
        final Connection jdbcConn = getSystemConnection().getJdbcConnection();
        Statement jobLogPosStmt = jdbcConn.createStatement();
        ResultSet posRs = jobLogPosStmt.executeQuery("SELECT COUNT(*)  FROM TABLE(QSYS2.JOBLOG_INFO('*')) A WHERE A.FROM_MODULE NOT IN ('QSQCALLSP') OR A.FROM_MODULE IS NULL ");
        posRs.next();
        int pos = posRs.getInt(1);
        posRs.close();
        jobLogPosStmt.close();
        Exception callException = null;
        try (CallableStatement clStmt = jdbcConn.prepareCall("CALL QSYS2.QCMDEXC(?)")) {
            clStmt.setString(1, cmd);
            try {
                clStmt.execute();
            } catch (Exception e) {
                callException = e;
            }
        }

        Statement resultsStmt = jdbcConn.createStatement();
        m_rs = resultsStmt.executeQuery(" SELECT MESSAGE_ID || ' [' || SUBSTR(MESSAGE_TYPE,0,5) || ']: ' || MESSAGE_TEXT as summary, SEVERITY, MESSAGE_SECOND_LEVEL_TEXT, MESSAGE_ID,MESSAGE_TYPE,MESSAGE_SUBTYPE,SEVERITY,MESSAGE_TIMESTAMP,FROM_LIBRARY,FROM_PROGRAM,FROM_MODULE,FROM_PROCEDURE,FROM_INSTRUCTION,TO_LIBRARY,TO_PROGRAM,TO_MODULE,TO_PROCEDURE,TO_INSTRUCTION,FROM_USER,MESSAGE_LIBRARY,MESSAGE_FILE,MESSAGE_TOKEN_LENGTH,MESSAGE_TOKENS,MESSAGE_TEXT,MESSAGE_SECOND_LEVEL_TEXT,MESSAGE_KEY,QUALIFIED_JOB_NAME  FROM TABLE(QSYS2.JOBLOG_INFO('*')) A WHERE A.FROM_MODULE NOT IN ('QSQCALLSP') OR A.FROM_MODULE IS NULL limit 99999 offset " + pos);
        addReplyData("metadata", getResultMetaDataForResponse());
        addReplyData("data", super.getNextDataBlock(Integer.MAX_VALUE));
        addReplyData("is_done", true);
        if(null != callException) {
            throw callException;
        }

    }

    PreparedStatement getStatement() {
        return m_stmt;
    }
}
