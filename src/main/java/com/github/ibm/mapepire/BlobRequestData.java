package com.github.ibm.mapepire;

public class BlobRequestData {

    private int replacementIndex;
    private int length;
    private int offset;

    public BlobRequestData(int replacementIndex, int length, int offset){
        this.replacementIndex = replacementIndex;
        this.length = length;
        this.offset = offset;
    }

    public int getLength() {
        return length;
    }

    public int getReplacementIndex() {
        return replacementIndex;
    }

    public int getOffset() {
        return offset;
    }
}
