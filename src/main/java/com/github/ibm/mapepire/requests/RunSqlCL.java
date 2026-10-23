package com.github.ibm.mapepire.requests;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.google.gson.JsonObject;

public class RunSqlCL extends ClientRequest {
    public RunSqlCL(final DataStreamProcessor _io, final SystemConnection _conn, final JsonObject _reqObj) {
        super(_io, _conn, _reqObj);
    }

    /**
     * Run the requested CL command through {@code QSYS2.QCMDEXC} on the JDBC connection and reply with the
     * job log messages it produced.
     *
     * @throws SQLException
     *             if the connection is restricted to basic queries, the job log cannot be read, or the
     *             command fails
     */
    @Override
    public void go() throws Exception {
        final SystemConnection systemConnection = getSystemConnection();
        if (systemConnection.isBasicQueryOnly()) {
            throw new SQLException("Only basic queries are allowed");
        }

        final String cmd = getRequestField("cmd").getAsString();
        final Connection jdbcConn = systemConnection.getJdbcConnection();
        int pos = 0;
        try (
            final Statement jobLogPosStmt = jdbcConn.createStatement();
            final ResultSet posRs = jobLogPosStmt.executeQuery("select max(ORDINAL_POSITION) from table(QSYS2.JOBLOG_INFO('*')) where FROM_MODULE is distinct from 'QSQCALLSP'")
        ) {
            if (posRs.next()) {
                pos = posRs.getInt(1);
            }
        }

        Exception callException = null;
        try (final CallableStatement clStmt = jdbcConn.prepareCall("call QSYS2.QCMDEXC(?)")) {
            clStmt.setString(1, cmd);
            try {
                clStmt.execute();
            } catch (final Exception _e) {
                // Deferred so the job log messages are still added to the reply before the failure is reported
                callException = _e;
            }
        }

        final List<RunCL.IBMiMessage> messages = new ArrayList<>();
        final String joblogSql = "select MESSAGE_ID, MESSAGE_TYPE, MESSAGE_TEXT, MESSAGE_SECOND_LEVEL_TEXT, SEVERITY, MESSAGE_TIMESTAMP, MESSAGE_LIBRARY, MESSAGE_FILE, FROM_PROGRAM, FROM_INSTRUCTION, TO_PROGRAM, TO_INSTRUCTION"
                + " from table(QSYS2.JOBLOG_INFO('*')) where FROM_MODULE is distinct from 'QSQCALLSP' and ORDINAL_POSITION > ?";
        try (final PreparedStatement joblogStmt = jdbcConn.prepareStatement(joblogSql)) {
            joblogStmt.setInt(1, pos);
            try (final ResultSet joblogRs = joblogStmt.executeQuery()) {
                while (joblogRs.next()) {
                    messages.add(new RunCL.IBMiMessage(joblogRs));
                }
            }
        }
        addReplyData("data", messages);

        if (null != callException) {
            if (callException instanceof SQLException && callException.getMessage().startsWith("[CPF0006]")) {
                // Keep exception message consistent with the one thrown by RunCL request, still throw the exception as SQLException to be consistent with the other SQL based requests
                final String sqlState = ((SQLException) callException).getSQLState();
                final int sqlCode = ((SQLException) callException).getErrorCode();
                throw new SQLException(new RunCL.CLExecutionException(messages).getMessage(), sqlState, sqlCode);
            }
            throw callException;
        }
    }

    @Override
    public boolean isForcedSynchronous() {
        return true;
    }
}
