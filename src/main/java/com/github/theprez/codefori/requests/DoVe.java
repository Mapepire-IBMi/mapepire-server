package com.github.theprez.codefori.requests;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;

import javax.naming.spi.DirStateFactory.Result;

import com.github.theprez.codefori.DataStreamProcessor;
import com.github.theprez.codefori.SystemConnection;
import com.github.theprez.codefori.Tracer;
import com.google.gson.JsonObject;

public class DoVe extends BlockRetrievableRequest {

    private Statement m_stmt = null;

    public DoVe(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    @Override
    public boolean isForcedSynchronous() {
        return true;
    }

    @Override
    public void go() throws Exception {
        final String sql = getRequestField("sql").getAsString();
        final Connection jdbcConn = getSystemConnection().getJdbcConnection();
        byte[] id = new byte[0];
        try(Statement s = jdbcConn.createStatement()) {
            s.execute("CALL QSYS2.QCMDEXC('STRDBMON OUTFILE(QTEMP/DOVEOUT)')");
            try (PreparedStatement tgt = jdbcConn.prepareStatement(sql)) {
            }

            s.execute("CALL QSYS2.QCMDEXC('ENDDBMON')");
            try (ResultSet rs =  s.executeQuery("SELECT QQJFLD, QQRID FROM QTEMP.DOVEOUT WHERE QQRID = 1000 limit 1")) {
                if(rs.next()) {
                    id = rs.getBytes(1);
                }
            }
            try {
                s.execute("drop table qtemp.QQ$DBVE_41");
            } catch (Exception e) {
                Tracer.info(e.getMessage());
            }
            try (CallableStatement callStmt = jdbcConn.prepareCall(
                    "call QIWS.QQQDBVE2(?,?,?,?,?)")) {
                callStmt.setBytes(1, id);
                callStmt.setString(2, "DOVEOUT   QTEMP     ISO-ISO..ENUBM0                           09");
                callStmt.registerOutParameter(3, Types.INTEGER);
                callStmt.registerOutParameter(4, Types.INTEGER);
                callStmt.registerOutParameter(5, Types.CHAR, 72);
                callStmt.execute();
            }
        }
        m_stmt = jdbcConn.createStatement();
        m_rs = m_stmt.executeQuery("SELECT * from qtemp.QQ$DBVE_41");
        addReplyData("metadata", getResultMetaDataForResponse());
        addReplyData("data", super.getNextDataBlock(Integer.MAX_VALUE));
        addReplyData("is_done", isDone());
    }

    Statement getStatement() {
        return m_stmt;
    }
}
