package com.github.ibm.mapepire;

import java.io.InputStream;

public class BlobResponseData {
    final InputStream is;
    final String columnName;
    final int rowId;
    final int length;

    public BlobResponseData(InputStream is, String columnName, int rowId, int length){
        this.is = is;
        this.columnName = columnName;
        this.rowId = rowId;
        this.length = length;
    }

    public InputStream getIs() {
        return is;
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
