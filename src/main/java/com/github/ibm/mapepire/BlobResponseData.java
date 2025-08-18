package com.github.ibm.mapepire;

import java.io.InputStream;

public class BlobResponseData {
    final InputStream is;
    final String columnName;
    final int rowId;

    public BlobResponseData(InputStream is, String columnName, int rowId){
        this.is = is;
        this.columnName = columnName;
        this.rowId = rowId;
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
}
