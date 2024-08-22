package com.github.ibm.mapepire.requests;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
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
        JsonArray parms = super.getRequestField("parameters").getAsJsonArray();
        boolean isBatch = !parms.isEmpty() && parms.get(0).isJsonArray();
        boolean hasResultSet = false;
        int batchUpdateCount = 0;

        PreparedStatement stmt = m_prev.getStatement();
        if (isBatch) {
            JsonArray arr = parms.getAsJsonArray();
            int batch_ops_added = 0;
            for (int i = 0; i < arr.size(); i++) {
                addJsonArrayParameters(stmt, arr.get(i).getAsJsonArray());
                m_prev.getStatement().addBatch();
            }
            batch_ops_added += arr.size();
            addReplyData("batch_added", batch_ops_added);
            int updateCount[] = stmt.executeBatch();
            batchUpdateCount = Arrays.stream(updateCount).sum();
        } else{
            if (parms != null) {
                addJsonArrayParameters(stmt, parms.getAsJsonArray());
            }
            hasResultSet = stmt.execute();
        }

        if (hasResultSet) {
            this.m_rs = stmt.getResultSet();
            final int numRows = super.getRequestFieldInt("rows", 1000);
            addReplyData("has_results", true);
            addReplyData("update_count", stmt.getLargeUpdateCount());
            addReplyData("metadata", getResultMetaDataForResponse());
            addReplyData("data", getNextDataBlock(numRows));
        } else {
            addReplyData("data", new LinkedList<Object>());
            addReplyData("has_results", false);
            addReplyData("update_count", batchUpdateCount);
        }
    }

    private void addJsonArrayParameters(PreparedStatement stmt, JsonArray arr) throws SQLException {
        for (int i = 1; i <= arr.size(); ++i) {
            JsonElement element = arr.get(-1 + i);
            if (element.isJsonNull()) {
                stmt.setNull(i, Types.NULL);
            } else {
                stmt.setString(i, element.getAsString());
            }
        }
    }

}
