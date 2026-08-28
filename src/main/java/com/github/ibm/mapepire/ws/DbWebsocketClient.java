package com.github.ibm.mapepire.ws;

import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.github.ibm.mapepire.Tracer;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.WebSocketException;

import java.io.*;
import java.util.concurrent.CountDownLatch;

public class DbWebsocketClient extends WebSocketAdapter {
  
  private final CountDownLatch closureLatch = new CountDownLatch(1);
  private final DataStreamProcessor io;
  private final Tracer tracer;

  DbWebsocketClient(String clientHost, String clientAddress, String host, String user, String pass) throws IOException {
    super();

    // Create per-connection tracer instance
    this.tracer = Tracer.getNew();
    
    SystemConnection conn = new SystemConnection(clientHost, clientAddress, host, user, pass, tracer);
    io = getDataStream(this, conn);
    
    // Log connection establishment
    tracer.logInfo("WebSocket connection established: " + tracer.getConnectionId() +
                   " (Client: " + clientHost + ", User: " + user + ")");
  }

  @Override
  public void onWebSocketConnect(Session sess) {
    super.onWebSocketConnect(sess);
    sess.setIdleTimeout(Integer.MAX_VALUE);
    tracer.logInfo("Socket Connected: " + sess + " [Connection ID: " + tracer.getConnectionId() + "]");
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
    
    // Log connection closure
    tracer.logInfo("WebSocket connection closed: " + tracer.getConnectionId() +
                   " (Status: " + statusCode + ", Reason: " + reason + ")");
    
    closureLatch.countDown();
  }

  @Override
  public void onWebSocketError(Throwable cause) {
    io.end();
    super.onWebSocketError(cause);
    // Log error to per-connection trace
    tracer.logErr("WebSocket error on connection " + tracer.getConnectionId() + ": " + cause.getMessage());
    tracer.logErr(cause);
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
              endpoint.tracer.logErr("Could not send message on connection " + endpoint.tracer.getConnectionId() + ": " + e.getMessage());
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