package com.github.ibm.mapepire.requests;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.github.ibm.mapepire.BlobRequestData;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.sql.rowset.serial.SerialBlob;

public class RunBlob{

    private final PrepareSql m_prev;

    public RunBlob(final byte[] binary, final List<BlobRequestData> blobRequestDataList, final PrepareSql _prev) throws SQLException {
        m_prev = _prev;
        PreparedStatement stmt = m_prev.getStatement();
        for (BlobRequestData blobRequestData: blobRequestDataList){
            int offset = blobRequestData.getOffset();
            int length = blobRequestData.getLength();
            int replacementIndex = blobRequestData.getReplacementIndex();
            InputStream is = new ByteArrayInputStream(binary, offset, length);
            stmt.setBinaryStream(replacementIndex, is, length);
        }
        stmt.execute();
    }
}
