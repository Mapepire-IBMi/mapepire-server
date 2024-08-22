package com.github.ibm.mapepire.requests;

import java.sql.CallableStatement;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedList;

import com.github.ibm.mapepire.DataStreamProcessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class PreparedExecute extends BlockRetrievableRequest {

    private final PrepareSql m_prev;

    public PreparedExecute(final DataStreamProcessor _io, final JsonObject _reqObj, final PrepareSql _prev) {
        super(_io, _prev.getSystemConnection(), _reqObj);
        m_prev = _prev;
    }

    @Override
    protected void go() throws Exception {
        JsonElement parms = super.getRequestField("parameters");
        boolean isBatch = super.getRequestFieldBoolean("batch", false);
        PreparedStatement stmt = m_prev.getStatement();
        if (isBatch) {
            if (null == parms) {
                stmt.executeLargeBatch();
                addReplyData("update_count", stmt.getLargeUpdateCount());
                return;
            }
            JsonArray arr = parms.getAsJsonArray();
            JsonElement firstElement = arr.get(0);
            int batch_ops_added = 0;
            if (firstElement.isJsonArray()) {
                for (int i = 1; i <= arr.size(); ++i) {
                    addJsonArrayParameters(stmt, arr.get(-1 + i).getAsJsonArray());
                    m_prev.getStatement().addBatch();
                }
                batch_ops_added += arr.size();
            } else {
                addJsonArrayParameters(stmt, arr);
                m_prev.getStatement().addBatch();
                batch_ops_added++;
            }
            addReplyData("batch_added", batch_ops_added);
            return;
        } else if (null != parms) {
            addJsonArrayParameters(stmt, parms.getAsJsonArray());
        }
        if (stmt.execute()) {
            this.m_rs = stmt.getResultSet();
            final int numRows = super.getRequestFieldInt("rows", 1000);
            addReplyData("has_results", true);
            addReplyData("update_count", stmt.getLargeUpdateCount());
            addReplyData("metadata", getResultMetaDataForResponse());
            addReplyData("data", getNextDataBlock(numRows));
            addReplyData("output_parms", getOutputParms(stmt));
            m_rs.close();
        } else {
            addReplyData("data", new LinkedList<Object>());
            addReplyData("has_results", false);
            addReplyData("update_count", stmt.getLargeUpdateCount());
            addReplyData("output_parms", getOutputParms(stmt));
        }
    }

    private void addJsonArrayParameters(PreparedStatement stmt, JsonArray arr) throws SQLException {
        for (int i = 1; i <= arr.size(); ++i) {
            JsonElement element = arr.get(-1 + i);
            if (element.isJsonNull()) {
                stmt.setNull(i, Types.NULL);
            } else {
                if (stmt instanceof CallableStatement
                        && ParameterMetaData.parameterModeOut == stmt.getParameterMetaData().getParameterMode(i)) {
                    ((CallableStatement) stmt).registerOutParameter(i, stmt.getParameterMetaData().getParameterType(i));
                } else {
                    stmt.setString(i, element.getAsString());
                }
            }
        }
    }

}
