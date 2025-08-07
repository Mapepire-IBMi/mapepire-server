package com.github.ibm.mapepire.requests;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;


import java.sql.Types;
import java.util.Arrays;
import java.util.Base64;
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
        JsonElement columnTypesElement = super.getRequestField("columnTypes");
        JsonArray columnTypes;
        if (columnTypesElement != null){
            columnTypes = columnTypesElement.getAsJsonArray();
        } else {
            columnTypes = null;
        }
        boolean isBatch = !parms.isEmpty() && parms.get(0).isJsonArray();
        boolean hasResultSet = false;
        long batchUpdateCount = 0;

        PreparedStatement stmt = m_prev.getStatement();
        if (isBatch) {
            JsonArray arr = parms.getAsJsonArray();
            int batch_ops_added = 0;
            for (int i = 0; i < arr.size(); i++) {
                addJsonArrayParameters(stmt, arr.get(i).getAsJsonArray(), columnTypes);
                m_prev.getStatement().addBatch();
            }
            batch_ops_added += arr.size();
            addReplyData("batch_added", batch_ops_added);
            long updateCount[] = stmt.executeLargeBatch();
            batchUpdateCount = Arrays.stream(updateCount).sum();
        } else{
            if (parms != null) {
                addJsonArrayParameters(stmt, parms.getAsJsonArray(), columnTypes);
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
            addReplyData("output_parms", getOutputParms(stmt));
            addReplyData("is_done", isDone());
        } else {
            addReplyData("data", new LinkedList<Object>());
            addReplyData("has_results", false);
            addReplyData("update_count", batchUpdateCount != 0 ? batchUpdateCount : stmt.getLargeUpdateCount());
            addReplyData("output_parms", getOutputParms(stmt));
            addReplyData("is_done", m_isDone = true);
        }
    }

    private void addParameter(int i, JsonElement parameter, ColumnType columnType, PreparedStatement stmt) throws SQLException {
        String stringValue = parameter.getAsString();

        switch (columnType) {
            case BLOB: {
                byte[] decodedBytes = Base64.getDecoder().decode(stringValue);
                ByteArrayInputStream blob = new ByteArrayInputStream(decodedBytes);
                stmt.setBlob(i, blob);
                break;
            }
            case CLOB:
                stmt.setClob(i, new StringReader(stringValue));
                break;
            case INTEGER:
                stmt.setInt(i, Integer.parseInt(stringValue));
                break;
            case BIGINT:
                stmt.setLong(i, Long.parseLong(stringValue));
                break;
            case SMALLINT:
                stmt.setShort(i, Short.parseShort(stringValue));
                break;
            case DOUBLE:
                stmt.setDouble(i, Double.parseDouble(stringValue));
                break;
            case FLOAT:
                stmt.setFloat(i, Float.parseFloat(stringValue));
                break;
            case DECIMAL:
                stmt.setBigDecimal(i, new BigDecimal(stringValue));
                break;
            case DATE:
                stmt.setDate(i, Date.valueOf(stringValue)); // expects ISO format: yyyy-[m]m-[d]d
                break;
            case TIME:
                stmt.setTime(i, Time.valueOf(stringValue)); // expects format: hh:mm:ss
                break;
            case TIMESTAMP:
                stmt.setTimestamp(i, Timestamp.valueOf(stringValue)); // expects format: yyyy-[m]m-[d]d hh:mm:ss[.f...]
                break;
            case BOOLEAN:
                stmt.setBoolean(i, Boolean.parseBoolean(stringValue));
                break;
            default:
                stmt.setString(i, stringValue); // Fallback
        }
    }


    public enum ColumnType {
        CHAR,
        VARCHAR,
        INTEGER,
        FLOAT,
        DECIMAL,
        DATE,
        TIME,
        TIMESTAMP,
        CLOB,
        BLOB,
        BOOLEAN,
        SMALLINT,
        BIGINT,
        DOUBLE
    }

    private ColumnType getColumnType(int i, JsonArray columnTypes){
        ColumnType columnType;
        try {
            columnType = ColumnType.valueOf(columnTypes.get(i).getAsString());
        } catch (IllegalArgumentException e){
            columnType = ColumnType.VARCHAR;
        }
        return columnType;
    }

    private void addJsonArrayParameters(PreparedStatement stmt, JsonArray parameters, JsonArray columnTypes) throws SQLException {
        for (int i = 1; i <= parameters.size(); ++i) {
            JsonElement element = parameters.get(-1 + i);
            ColumnType columnType = columnTypes == null ? ColumnType.VARCHAR : getColumnType(-1 + i, columnTypes);

            if (element.isJsonNull()) {
                stmt.setNull(i, Types.NULL);
            } else {
                if (stmt instanceof CallableStatement
                        && ParameterMetaData.parameterModeOut == stmt.getParameterMetaData().getParameterMode(i)) {
                    ((CallableStatement) stmt).registerOutParameter(i, stmt.getParameterMetaData().getParameterType(i));
                } else if (stmt instanceof CallableStatement
                        && ParameterMetaData.parameterModeInOut == stmt.getParameterMetaData().getParameterMode(i)) {
                    ((CallableStatement) stmt).registerOutParameter(i, stmt.getParameterMetaData().getParameterType(i));
                    addParameter(i, element, columnType, stmt);
                } else {
                    addParameter(i, element, columnType, stmt);
                }
            }
        }
    }

}
