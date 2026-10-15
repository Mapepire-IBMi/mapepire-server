package com.github.ibm.mapepire.requests;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.google.gson.JsonObject;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class RunSqlCL extends ClientRequest {
    public RunSqlCL(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj) {
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
        int pos = 0;
        try(
            final Statement jobLogPosStmt = jdbcConn.createStatement();
            final ResultSet posRs = jobLogPosStmt.executeQuery("select max(ORDINAL_POSITION) from table(QSYS2.JOBLOG_INFO('*')) where FROM_MODULE is distinct from 'QSQCALLSP'")
        ) {
            if(posRs.next()) {
                pos = posRs.getInt(1);
            }
        }

        Exception callException = null;
        try (CallableStatement clStmt = jdbcConn.prepareCall("call QSYS2.QCMDEXC(?)")) {
            clStmt.setString(1, cmd);
            try {
                clStmt.execute();
            }
            catch (Exception e) {
                callException = e;
            }
        }

        final List<RunCL.IBMiMessage> messages = new ArrayList<>();
        try(PreparedStatement joblogStmt = jdbcConn.prepareStatement("select MESSAGE_ID, MESSAGE_TYPE, MESSAGE_TEXT, MESSAGE_SECOND_LEVEL_TEXT, SEVERITY, MESSAGE_TIMESTAMP, MESSAGE_LIBRARY, MESSAGE_FILE, FROM_PROGRAM, FROM_INSTRUCTION, TO_PROGRAM, TO_INSTRUCTION from table(QSYS2.JOBLOG_INFO('*')) where FROM_MODULE is distinct from 'QSQCALLSP' and ORDINAL_POSITION > ?")) {
            joblogStmt.setInt(1, pos);
            try(ResultSet joblogRs = joblogStmt.executeQuery()){
                while(joblogRs.next()) {
                    messages.add(new RunCL.IBMiMessage(joblogRs));
                }
            }
        }
        addReplyData("messages", messages);

        if(callException != null) {
            if(callException instanceof SQLException && callException.getMessage().startsWith("[CPF0006]")) {
                //Keep exception message consistent with the one thrown by RunCL request, still throw the exception as SQLException to be consistent with the other SQL based requests
                final String sqlState = ((SQLException) callException).getSQLState();
                final int sqlCode = ((SQLException) callException).getErrorCode();
                throw new SQLException(new RunCL.CLExecutionException(messages).getMessage(), sqlState, sqlCode);
            }
            throw callException;
        }
    }
}
