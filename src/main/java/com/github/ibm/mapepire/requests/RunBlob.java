package com.github.ibm.mapepire.requests;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.*;
import java.util.Arrays;
import java.util.LinkedList;

import com.github.ibm.mapepire.DataStreamProcessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.sql.rowset.serial.SerialBlob;

public class RunBlob{

    private final PrepareSql m_prev;

    public RunBlob(final DataStreamProcessor _io, final byte[] binary, final PrepareSql _prev) throws SQLException {
        m_prev = _prev;
        PreparedStatement stmt = m_prev.getStatement();
        try (InputStream is = new ByteArrayInputStream(binary)) {
            stmt.setBinaryStream(1, is, binary.length);
            int affectedRows = stmt.executeUpdate();
        }
        catch (Exception e){
            System.out.println("Caught error " + e);
        }

    }

//    @Override
//    protected void go() throws Exception {
//        boolean isBatch = !parms.isEmpty() && parms.get(0).isJsonArray();
//        boolean hasResultSet = false;
//        long batchUpdateCount = 0;
//
//        PreparedStatement stmt = m_prev.getStatement();
//        if (isBatch) {
//            JsonArray arr = parms.getAsJsonArray();
//            int batch_ops_added = 0;
//            for (int i = 0; i < arr.size(); i++) {
//                addJsonArrayParameters(stmt, arr.get(i).getAsJsonArray());
//                m_prev.getStatement().addBatch();
//            }
//            batch_ops_added += arr.size();
//            addReplyData("batch_added", batch_ops_added);
//            long updateCount[] = stmt.executeLargeBatch();
//            batchUpdateCount = Arrays.stream(updateCount).sum();
//        } else{
//            if (parms != null) {
//                addJsonArrayParameters(stmt, parms.getAsJsonArray());
//            }
//            hasResultSet = stmt.execute();
//        }
//
//        if (hasResultSet) {
//            this.m_rs = stmt.getResultSet();
//            final int numRows = super.getRequestFieldInt("rows", 1000);
//            addReplyData("has_results", true);
//            addReplyData("update_count", stmt.getLargeUpdateCount());
//            addReplyData("metadata", getResultMetaDataForResponse());
//            addReplyData("data", getNextDataBlock(numRows));
//            addReplyData("output_parms", getOutputParms(stmt));
//            addReplyData("is_done", isDone());
//        } else {
//            addReplyData("data", new LinkedList<Object>());
//            addReplyData("has_results", false);
//            addReplyData("update_count", batchUpdateCount != 0 ? batchUpdateCount : stmt.getLargeUpdateCount());
//            addReplyData("output_parms", getOutputParms(stmt));
//            addReplyData("is_done", m_isDone = true);
//        }
//    }

    private void addJsonArrayParameters(PreparedStatement stmt, JsonArray arr) throws SQLException {
        for (int i = 1; i <= arr.size(); ++i) {
            JsonElement element = arr.get(-1 + i);
            if (element.isJsonNull()) {
                stmt.setNull(i, Types.NULL);
            } else {
                if (stmt instanceof CallableStatement
                        && ParameterMetaData.parameterModeOut == stmt.getParameterMetaData().getParameterMode(i)) {
                    ((CallableStatement) stmt).registerOutParameter(i, stmt.getParameterMetaData().getParameterType(i));
                } else if (stmt instanceof CallableStatement
                        && ParameterMetaData.parameterModeInOut == stmt.getParameterMetaData().getParameterMode(i)) {
                    ((CallableStatement) stmt).registerOutParameter(i, stmt.getParameterMetaData().getParameterType(i));
                    stmt.setString(i, element.getAsString());
                } else {
                    stmt.setString(i, element.getAsString());
                }
            }
        }
    }

}
