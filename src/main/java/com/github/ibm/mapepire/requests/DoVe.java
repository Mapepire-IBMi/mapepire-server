package com.github.ibm.mapepire.requests;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.github.ibm.mapepire.Tracer;
import com.google.gson.JsonObject;

public class DoVe extends BlockRetrievableRequest {

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
        boolean isRunning = getRequestFieldBoolean("run", false);
        final int numRows = super.getRequestFieldInt("rows", 1000);
        byte[] id = new byte[0];

        try (Statement dbMonStmt = jdbcConn.createStatement();
                Statement qaqqiniStmt = jdbcConn.createStatement()) {
            dbMonStmt.execute("CALL QSYS2.QCMDEXC('STRDBMON OUTFILE(QTEMP/QDOVEOUT)')");
            try {
                qaqqiniStmt.execute("CALL QSYS2.OVERRIDE_QAQQINI(1, '', '')");
                if (!isRunning) {
                    qaqqiniStmt.execute("CALL QSYS2.OVERRIDE_QAQQINI(2, 'QUERY_TIME_LIMIT', '0')");
                }
                //qaqqiniStmt.execute("CALL QSYS2.OVERRIDE_QAQQINI(2, 'SQL_STMT_REUSE','255')");
                qaqqiniStmt.execute("CALL QSYS2.OVERRIDE_QAQQINI(2, 'OPEN_CURSOR_CLOSE_COUNT','65535')");
                qaqqiniStmt.execute("CALL QSYS2.OVERRIDE_QAQQINI(2, 'OPEN_CURSOR_THRESHOLD','-1')");
                PreparedStatement tgt = jdbcConn.prepareStatement(sql);
                try {
                    tgt.execute();
                    if (isRunning) {
                        this.m_rs = tgt.getResultSet();
                    }
                } catch (SQLException e) {
                    if (!"57005".equals(e.getSQLState())) {
                        throw e;
                    }
                } finally {
                    if (!isRunning) {
                        try {
                            tgt.close();
                        } catch (Exception e) {
                            Tracer.warn(e);
                        }
                    }
                }
            } finally {
                qaqqiniStmt.execute("CALL QSYS2.OVERRIDE_QAQQINI(3, '', '')");
                dbMonStmt.execute("CALL QSYS2.QCMDEXC('ENDDBMON')");
            }
            try (ResultSet rs = dbMonStmt.executeQuery("SELECT QQJFLD FROM QTEMP.QDOVEOUT WHERE QQRID = 3014 lImIt 1")) {
                if (rs.next()) {
                    id = rs.getBytes(1);
                }
            }
        }

        try (CallableStatement callStmt = jdbcConn.prepareCall(
                "call QSYS.QQQDBVE(?,?,?,?)")) {
            callStmt.setBytes(1, id);
            callStmt.setString(2, "QDOVEOUT  QTEMP     ISO-ISO..ENUBM0                           01");
            callStmt.registerOutParameter(3, Types.INTEGER);
            callStmt.registerOutParameter(4, Types.INTEGER);

            if (!callStmt.execute()) {
                throw new RuntimeException("No result set available");
            }
            ResultSet veData = callStmt.getResultSet();
            addReplyData("vemetadata", getResultMetaDataForResponse(veData.getMetaData(), getSystemConnection()));
            addReplyData("vedata", super.getNextDataBlock(veData, Integer.MAX_VALUE, m_isTerseData).getData());
            if (null != this.m_rs) {
                addReplyData("metadata", getResultMetaDataForResponse());
                addReplyData("data", super.getNextDataBlock(numRows));
                addReplyData("is_done", isDone());
            } else {
                addReplyData("is_done", true);
            }
        }

        try (Statement dbMonStmt = jdbcConn.createStatement()) {
            dbMonStmt.execute("drop table QTEMP.QDOVEOUT");
        } catch (Exception e) {
            Tracer.info(e.getMessage());
        }
    }
}
