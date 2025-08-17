package com.github.ibm.mapepire.ws;

import java.io.IOException;
import java.nio.ByteBuffer;

@FunctionalInterface
public interface BinarySender {
    void send(ByteBuffer buffer, boolean isLast) throws IOException;
}