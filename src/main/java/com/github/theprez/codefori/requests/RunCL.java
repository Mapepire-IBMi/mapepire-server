package com.github.theprez.codefori.requests;

import java.sql.CallableStatement;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.github.theprez.codefori.ClientRequest;
import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.google.gson.JsonObject;
import com.ibm.as400.access.AS400JDBCConnection;
import com.ibm.as400.access.AS400JDBCPreparedStatement;

public class RunCL extends BlockRetrievableRequest {

    private AS400JDBCPreparedStatement m_stmt = null;

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
        final AS400JDBCConnection jdbcConn = getSystemConnection().getJdbcConnection();
        Statement jobLogPosStmt = jdbcConn.createStatement();
        ResultSet posRs = jobLogPosStmt.executeQuery("SELECT COUNT(*)  FROM TABLE(QSYS2.JOBLOG_INFO('*')) A ");
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
        m_rs = resultsStmt.executeQuery(" SELECT MESSAGE_ID || ' [' || SUBSTR(MESSAGE_TYPE,0,5) || ']: ' || MESSAGE_TEXT as summary, SEVERITY, MESSAGE_SECOND_LEVEL_TEXT, MESSAGE_ID,MESSAGE_TYPE,MESSAGE_SUBTYPE,SEVERITY,MESSAGE_TIMESTAMP,FROM_LIBRARY,FROM_PROGRAM,FROM_MODULE,FROM_PROCEDURE,FROM_INSTRUCTION,TO_LIBRARY,TO_PROGRAM,TO_MODULE,TO_PROCEDURE,TO_INSTRUCTION,FROM_USER,MESSAGE_LIBRARY,MESSAGE_FILE,MESSAGE_TOKEN_LENGTH,MESSAGE_TOKENS,MESSAGE_TEXT,MESSAGE_SECOND_LEVEL_TEXT,MESSAGE_KEY,QUALIFIED_JOB_NAME  FROM TABLE(QSYS2.JOBLOG_INFO('*')) A limit 99999 offset " + pos);
        addReplyData("data", super.getNextDataBlock(Integer.MAX_VALUE));
        addReplyData("is_done", true);
        if(null != callException) {
            throw callException;
        }

    }

    AS400JDBCPreparedStatement getStatement() {
        return m_stmt;
    }
}
