package com.github.ibm.mapepire;

import java.io.InputStream;
import java.sql.Blob;

public class BlobResponseData {
    final Blob blob;
    final String columnName;
    final int rowId;
    final int length;

    public BlobResponseData(Blob is, String columnName, int rowId, int length){
        this.blob = is;
        this.columnName = columnName;
        this.rowId = rowId;
        this.length = length;
    }

    public Blob getBlob() {
        return blob;
    }

    public String getColumnName(){
        return columnName;
    }

    public int getRowId(){
        return rowId;
    }

    public int getLength(){
        return length;
    }
}
