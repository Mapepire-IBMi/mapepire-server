package com.github.ibm.mapepire.ws;

import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.WebSocketException;

import java.io.*;
import java.util.concurrent.CountDownLatch;

public class DbWebsocketClient extends WebSocketAdapter {
  private final CountDownLatch closureLatch = new CountDownLatch(1);
  private final DataStreamProcessor io;

  DbWebsocketClient(String clientHost, String clientAddress, String host, String user, String pass) throws IOException {
    super();
    SystemConnection conn = new SystemConnection(clientHost, clientAddress,host, user, pass);
    io = getDataStreamProcessor(this, conn);
  }

  @Override
  public void onWebSocketConnect(Session sess) {
    super.onWebSocketConnect(sess);
    sess.setIdleTimeout(Integer.MAX_VALUE);
    System.out.println("Socket Connected: " + sess);
  }

  @Override
  public void onWebSocketText(String message) {
    super.onWebSocketText(message);
    io.run(message);
  }

  @Override
  public void onWebSocketBinary(byte[] payload, int offset, int len) {
    io.run(payload, offset, len);
  }

  @Override
  public void onWebSocketClose(int statusCode, String reason) {
    io.end();
    super.onWebSocketClose(statusCode, reason);
    closureLatch.countDown();
  }

  @Override
  public void onWebSocketError(Throwable cause) {
    io.end();
    super.onWebSocketError(cause);
    // cause.printStackTrace(System.err);
  }

  public void awaitClosure() throws InterruptedException {
    closureLatch.await();
  }

  private DataStreamProcessor getDataStreamProcessor(DbWebsocketClient endpoint, SystemConnection conn) throws UnsupportedEncodingException {
    AsyncSender asyncSender = new AsyncSender(this);

    InputStream in = new ByteArrayInputStream(new byte[0]);

    OutputStream outStreamText = new OutputStream() {
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
              System.err.println("Could not send message due to error: " + e.getMessage());
            }
          }
        }
        this.payload.reset();
      }
    };

    PrintStream out = new PrintStream(outStreamText);

    return new DataStreamProcessor(in, out, asyncSender, conn, false);
  }
}
