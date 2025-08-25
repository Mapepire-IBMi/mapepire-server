package com.github.ibm.mapepire.ws;

import java.nio.ByteBuffer;
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
        executor.submit(() -> {
            try {
                RemoteEndpoint remote = session.getRemote();
                remote.sendPartialBytes(buffer, isLast);
            } catch (Exception e) {
                e.printStackTrace(); // or better logging
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
