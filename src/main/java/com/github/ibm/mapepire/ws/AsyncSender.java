package com.github.ibm.mapepire.ws;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;

public class AsyncSender {
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final WebSocketAdapter session;

    public AsyncSender(WebSocketAdapter session) {
        this.session = session;
    }

    public void send(ByteBuffer buffer, boolean isLast) {
        try {

            executor.submit(() -> {
                RemoteEndpoint remote = session.getRemote();
                try {
                    remote.sendPartialBytes(buffer, isLast);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        ).get();}
            catch (Exception e) {
            e.printStackTrace(); // or better logging
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
