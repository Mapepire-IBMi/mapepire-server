package com.github.ibm.mapepire.ws;

import com.github.ibm.mapepire.ConnectionTraceContext;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.github.ibm.mapepire.Tracer;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.WebSocketException;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public class DbWebsocketClient extends WebSocketAdapter {
  private final CountDownLatch closureLatch = new CountDownLatch(1);
  private final DataStreamProcessor io;
  private final String connectionId; // ✅ NEW: Unique ID for per-connection tracing

  DbWebsocketClient(String clientHost, String clientAddress, String host, String user, String pass) throws IOException {
    super();
    // ✅ NEW: Generate unique connection ID
    this.connectionId = UUID.randomUUID().toString();
    
    SystemConnection conn = new SystemConnection(clientHost, clientAddress, host, user, pass);
    io = getDataStream(this, conn);
    
    // ✅ NEW: Initialize per-connection trace context
    Tracer.get().setConnectionId(connectionId);
    Tracer.info("WebSocket connection established: " + connectionId + 
                " (Client: " + clientHost + ", User: " + user + ")");
  }

  @Override
  public void onWebSocketConnect(Session sess) {
    super.onWebSocketConnect(sess);
    sess.setIdleTimeout(Integer.MAX_VALUE);
    Tracer.info("Socket Connected: " + sess + " [Connection ID: " + connectionId + "]");
  }

  @Override
  public void onWebSocketText(String message) {
    super.onWebSocketText(message);
    io.run(message);
  }

  @Override
  public void onWebSocketClose(int statusCode, String reason) {
    io.end();
    super.onWebSocketClose(statusCode, reason);
    
    // ✅ NEW: Log connection closure
    Tracer.info("WebSocket connection closed: " + connectionId + 
                " (Status: " + statusCode + ", Reason: " + reason + ")");
    
    // ✅ NEW: Cleanup per-connection trace context
    ConnectionTraceContext.remove(connectionId);
    closureLatch.countDown();
  }

  @Override
  public void onWebSocketError(Throwable cause) {
    io.end();
    super.onWebSocketError(cause);
    // ✅ NEW: Log error to per-connection trace
    Tracer.err("WebSocket error on connection " + connectionId + ": " + cause.getMessage());
    Tracer.err(cause);
  }

  public void awaitClosure() throws InterruptedException {
    closureLatch.await();
  }

  private static DataStreamProcessor getDataStream(DbWebsocketClient endpoint, SystemConnection conn) throws UnsupportedEncodingException {
    InputStream in = new ByteArrayInputStream(new byte[0]);

    OutputStream outStream = new OutputStream() {
      private final ByteArrayOutputStream payload = new ByteArrayOutputStream();

      @Override
      public synchronized void write(int b) {
        this.payload.write((byte)b);
      }

      public byte[] getBytes() {
        return payload.toByteArray();
      }

      @Override
      public synchronized void flush() throws IOException {
        if (endpoint.getRemote() != null) {
          if (payload.size() != 0) {
            String message = this.payload.toString("UTF-8") + "\n";
            try {
              endpoint.getRemote().sendString(message);
            } catch (WebSocketException e){
              Tracer.err("Could not send message on connection " + endpoint.connectionId + ": " + e.getMessage());
            }
          }
        }
        this.payload.reset();
      }
    };

    PrintStream out = new PrintStream(outStream);

    return new DataStreamProcessor(in, out, conn, false);
  }
}