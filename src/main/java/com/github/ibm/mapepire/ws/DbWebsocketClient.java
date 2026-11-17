package com.github.ibm.mapepire.ws;

import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.MapepireServer;
import com.github.ibm.mapepire.SystemConnection;
import com.github.ibm.mapepire.Tracer;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.WebSocketException;
import org.eclipse.jetty.websocket.api.WebSocketPingPongListener;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class DbWebsocketClient extends WebSocketAdapter implements WebSocketPingPongListener {
  
  private final CountDownLatch closureLatch = new CountDownLatch(1);
  private final DataStreamProcessor io;
  private final Tracer tracer;

  /** Shared scheduler for all heartbeat tasks (daemon threads, so they don't prevent JVM exit). */
  private static final ScheduledExecutorService s_heartbeatScheduler =
      Executors.newScheduledThreadPool(1, r -> {
          Thread t = new Thread(r, "MapepireHeartbeat");
          t.setDaemon(true);
          return t;
      });

  /** Timestamp (ms) of the last pong frame received from this client. Initialised to "now" on connect. */
  private final AtomicLong lastPongReceivedAt = new AtomicLong(0);

  /** Set to true once the connection is being torn down, to suppress redundant close attempts. */
  private final AtomicBoolean closing = new AtomicBoolean(false);

  /** The periodic heartbeat task for this connection, cancelled on close. */
  private ScheduledFuture<?> heartbeatTask;

  DbWebsocketClient(String clientHost, String clientAddress, String host, String user, char[] pass, String rawCredentials) throws IOException {
    super();

    // Create per-connection tracer instance
    this.tracer = Tracer.getNew();
    
    SystemConnection conn = new SystemConnection(clientHost, clientAddress, host, user, pass, tracer);
    conn.setRawCredentials(rawCredentials);
    io = getDataStream(this, conn);
    
    // Log connection establishment
    tracer.logInfo("WebSocket connection established: " + tracer.getConnectionId() +
                   " (Client: " + clientHost + ", User: " + user + ")");
  }

  @Override
  public void onWebSocketConnect(Session sess) {
    super.onWebSocketConnect(sess);
    sess.setIdleTimeout(MapepireServer.getWsIdleTimeoutMillis());
    tracer.logInfo("Socket Connected: " + sess + " [Connection ID: " + tracer.getConnectionId() + "]");

    // Treat the moment of connect as the first implicit pong so that the first heartbeat
    // check does not immediately evict a freshly connected client.
    lastPongReceivedAt.set(System.currentTimeMillis());

    final long intervalMs = MapepireServer.getHeartbeatIntervalMillis();
    if (intervalMs > 0) {
      heartbeatTask = s_heartbeatScheduler.scheduleAtFixedRate(
          this::runHeartbeat, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }
  }

  @Override
  public void onWebSocketPing(ByteBuffer payload) {
    // Server-initiated pings only; nothing to do here.
  }

  @Override
  public void onWebSocketPong(ByteBuffer payload) {
    lastPongReceivedAt.set(System.currentTimeMillis());
  }

  @Override
  public void onWebSocketText(String message) {
    super.onWebSocketText(message);
    io.run(message);
  }

  @Override
  public void onWebSocketClose(int statusCode, String reason) {
    cancelHeartbeat();
    io.end();
    super.onWebSocketClose(statusCode, reason);
    
    // Log connection closure
    tracer.logInfo("WebSocket connection closed: " + tracer.getConnectionId() +
                   " (Status: " + statusCode + ", Reason: " + reason + ")");
    
    closureLatch.countDown();
  }

  @Override
  public void onWebSocketError(Throwable cause) {
    cancelHeartbeat();
    io.end();
    super.onWebSocketError(cause);
    // Log error to per-connection trace
    tracer.logErr("WebSocket error on connection " + tracer.getConnectionId() + ": " + cause.getMessage());
    tracer.logErr(cause);
  }

  public void awaitClosure() throws InterruptedException {
    closureLatch.await();
  }

  // ---------------------------------------------------------------------------
  // Heartbeat helpers
  // ---------------------------------------------------------------------------

  /**
   * Called periodically by the scheduler. Sends a WS ping frame and checks
   * whether the previous ping was answered in time. If the client has been
   * silent for longer than the configured timeout the session is closed.
   */
  private void runHeartbeat() {
    final Session sess = getSession();
    if (sess == null || !sess.isOpen()) {
      cancelHeartbeat();
      return;
    }

    final long silenceMs = System.currentTimeMillis() - lastPongReceivedAt.get();
    final long timeoutMs = MapepireServer.getHeartbeatTimeoutMillis();

    if (silenceMs > timeoutMs) {
      if (closing.compareAndSet(false, true)) {
        Tracer.globalWarn("Heartbeat timeout on connection " + tracer.getConnectionId() +
                          " (no pong for " + silenceMs + " ms). Closing session.");
        cancelHeartbeat();
        try {
          sess.close(1001, "Heartbeat timeout");
        } catch (Exception e) {
          tracer.logErr("Error closing timed-out session " + tracer.getConnectionId() + ": " + e.getMessage());
        }
      }
      return;
    }

    // Send ping – errors here are non-fatal; the next check cycle will catch a dead connection.
    try {
      sess.getRemote().sendPing(ByteBuffer.allocate(0));
    } catch (Exception e) {
      tracer.logErr("Could not send heartbeat ping on connection " + tracer.getConnectionId() + ": " + e.getMessage());
    }
  }

  private void cancelHeartbeat() {
    final ScheduledFuture<?> task = heartbeatTask;
    if (task != null) {
      task.cancel(false);
    }
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