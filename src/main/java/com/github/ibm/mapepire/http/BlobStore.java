package com.github.ibm.mapepire.http;

import com.github.ibm.mapepire.Tracer;

import java.io.*;
import java.nio.file.Files;
import java.sql.Blob;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Singleton store for BLOB tokens.
 *
 * <p>BLOBs &lt;= {@link #MEMORY_THRESHOLD_BYTES} are held in a {@code byte[]}.
 * BLOBs above that threshold are spooled to a JVM temp file in a background
 * thread so that the WebSocket response (carrying the {@code blob_url}) is
 * returned to the client immediately, without waiting for the full spool to
 * complete. The HTTP GET on {@code /blob/{token}} will wait (up to the TTL)
 * for the spool to finish before streaming bytes.</p>
 *
 * <p>Each entry carries the Basic-Auth credentials of the connection that
 * produced it so that {@link BlobServlet} can re-validate the caller.</p>
 *
 * <p>A background thread sweeps expired entries every 30 seconds.</p>
 */
public class BlobStore {

    // BLOBs larger than this are spooled to disk instead of held in memory
    public static final int MEMORY_THRESHOLD_BYTES = 1024 * 1024; // 1 MB

    // Default TTL in seconds — overridable via BLOB_TOKEN_TTL env var
    private static volatile long s_ttlSeconds = 60;

    private static final BlobStore s_instance = new BlobStore();

    private final Map<String, BlobEntry> m_entries = new ConcurrentHashMap<>();
    private final ScheduledExecutorService m_sweeper =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "BlobStore-sweeper");
                t.setDaemon(true);
                return t;
            });

    private BlobStore() {
        // Read TTL from environment on startup; setTtlSeconds() can override later
        String envTtl = System.getenv("BLOB_TOKEN_TTL");
        if (envTtl != null && !envTtl.isEmpty()) {
            try {
                s_ttlSeconds = Long.parseLong(envTtl.trim());
            } catch (NumberFormatException e) {
                Tracer.warn("Invalid BLOB_TOKEN_TTL value '" + envTtl + "', using default " + s_ttlSeconds + "s");
            }
        }
        m_sweeper.scheduleAtFixedRate(this::sweepExpired, 30, 30, TimeUnit.SECONDS);
    }

    public static BlobStore getInstance() {
        return s_instance;
    }

    // -------------------------------------------------------------------------
    // TTL configuration (read-only at runtime — set via BLOB_TOKEN_TTL env var)
    // -------------------------------------------------------------------------

    public static long getTtlSeconds() {
        return s_ttlSeconds;
    }

    // -------------------------------------------------------------------------
    // Storing blobs
    // -------------------------------------------------------------------------

    /**
     * Store raw bytes and return a single-use token.
     *
     * @param data        the BLOB bytes
     * @param credentials Base64-encoded "user:pass" copied from the WebSocket
     *                    Authorization header — used to re-validate on retrieval
     * @return opaque token (UUID string) to embed in the response as a URL path segment
     */
    public String store(byte[] data, String credentials) throws IOException {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(s_ttlSeconds);

        BlobEntry entry;
        if (data.length <= MEMORY_THRESHOLD_BYTES) {
            entry = BlobEntry.ofBytes(data, expiresAt, credentials);
        } else {
            entry = BlobEntry.ofFile(data, expiresAt, credentials);
        }
        m_entries.put(token, entry);
        Tracer.info("BlobStore: stored token " + token + " size=" + data.length + " expires=" + expiresAt);
        return token;
    }

    /**
     * Store a BLOB from a live JDBC {@link Blob} object.
     *
     * <p>For blobs above {@link #MEMORY_THRESHOLD_BYTES} the spool to disk runs
     * in a background thread so this method returns — and the caller can send the
     * WebSocket response with the {@code blob_url} — immediately, without waiting
     * for all bytes to land on disk. The HTTP GET on {@code /blob/{token}} will
     * block until the spool is complete (or the TTL elapses).</p>
     *
     * <p>The {@link Blob} stream is opened <em>inside</em> the spool thread for
     * large blobs so that the JDBC cursor can advance freely once this method
     * returns. {@link Blob#free()} is called by the spool thread when it finishes.</p>
     *
     * <p>For small blobs the bytes are read synchronously into memory before
     * returning, then {@link Blob#free()} is called immediately.</p>
     */
    public String store(Blob blob, long length, String credentials) throws IOException {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(s_ttlSeconds);

        BlobEntry entry;
        if (length <= MEMORY_THRESHOLD_BYTES) {
            // Small blob — materialise into heap now so the Blob/cursor can be freed immediately
            try {
                byte[] bytes = readAllBytes(blob.getBinaryStream());
                blob.free();
                entry = BlobEntry.ofBytes(bytes, expiresAt, credentials);
            } catch (SQLException e) {
                throw new IOException("Failed to read BLOB data", e);
            }
            m_entries.put(token, entry);
            Tracer.info("BlobStore: stored token " + token + " size=" + length + " expires=" + expiresAt);
        } else {
            // Large blob — register token immediately, spool to disk in background.
            // The Blob object is passed to the spool thread which opens the stream
            // and calls free() itself — the caller must NOT free the Blob after this.
            entry = BlobEntry.ofBlobAsync(blob, length, expiresAt, credentials, token);
            m_entries.put(token, entry);
            Tracer.info("BlobStore: registered token " + token + " size=" + length
                    + " expires=" + expiresAt + " (spool in progress)");
        }
        return token;
    }

    // -------------------------------------------------------------------------
    // Retrieving blobs
    // -------------------------------------------------------------------------

    /**
     * Retrieve and <em>consume</em> a token. Returns {@code null} if the token
     * is unknown or has expired. The entry is removed immediately on retrieval
     * (single-use) and any temp file is deleted after streaming.
     *
     * <p>If the backing spool is still in progress, this method returns the
     * entry anyway — {@link BlobEntry#openStream()} will block until the spool
     * completes or the TTL elapses.</p>
     */
    public BlobEntry consume(String token) {
        BlobEntry entry = m_entries.remove(token);
        if (entry == null) {
            return null;
        }
        if (Instant.now().isAfter(entry.expiresAt)) {
            entry.cleanup();
            return null;
        }
        return entry;
    }

    // -------------------------------------------------------------------------
    // Java 8 compatible helpers
    // -------------------------------------------------------------------------

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[65536];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buf.write(chunk, 0, read);
        }
        return buf.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Expiry sweep
    // -------------------------------------------------------------------------

    private void sweepExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, BlobEntry>> it = m_entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, BlobEntry> e = it.next();
            if (now.isAfter(e.getValue().expiresAt)) {
                Tracer.info("BlobStore: expiring token " + e.getKey());
                e.getValue().cleanup();
                it.remove();
            }
        }
    }

    // -------------------------------------------------------------------------
    // BlobEntry — internal value type
    // -------------------------------------------------------------------------

    public static class BlobEntry {
        // Exactly one of these is set once the entry is ready
        private volatile byte[] m_bytes;
        private volatile File   m_file;

        public final long size;
        public final Instant expiresAt;
        public final String credentials; // Base64 "user:pass"

        /**
         * Latch that is counted down to zero when the backing data is fully
         * available (either already at construction for in-memory entries, or
         * when the background spool thread finishes for large blobs).
         */
        private final CountDownLatch m_ready;

        /**
         * Non-null if the background spool thread encountered an error.
         * Checked by {@link #openStream()} after the latch releases.
         */
        private volatile IOException m_spoolError;

        private BlobEntry(byte[] bytes, File file, long size, Instant expiresAt,
                          String credentials, CountDownLatch ready) {
            this.m_bytes      = bytes;
            this.m_file       = file;
            this.size         = size;
            this.expiresAt    = expiresAt;
            this.credentials  = credentials;
            this.m_ready      = ready;
        }

        // -- factories --------------------------------------------------------

        static BlobEntry ofBytes(byte[] bytes, Instant expiresAt, String credentials) {
            // Already ready — latch starts at 0
            CountDownLatch ready = new CountDownLatch(0);
            return new BlobEntry(bytes, null, bytes.length, expiresAt, credentials, ready);
        }

        static BlobEntry ofFile(byte[] bytes, Instant expiresAt, String credentials) throws IOException {
            File tmp = Files.createTempFile("mapepire-blob-", ".tmp").toFile();
            tmp.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(bytes);
            }
            // Already ready — latch starts at 0
            CountDownLatch ready = new CountDownLatch(0);
            return new BlobEntry(null, tmp, bytes.length, expiresAt, credentials, ready);
        }

        /**
         * Create an entry whose data is spooled to a temp file in a background
         * thread from a live JDBC {@link Blob}. The entry is returned immediately
         * so the caller can register the token and send the WebSocket response
         * without waiting.
         *
         * <p>The {@link Blob} stream is opened inside the thread (not before),
         * so the JDBC cursor is free to advance as soon as this method returns.
         * {@link Blob#free()} is called by the thread in its {@code finally} block.</p>
         *
         * <p>{@link #openStream()} will block (up to the TTL) until the spool
         * thread signals the latch.</p>
         */
        static BlobEntry ofBlobAsync(Blob blob, long declaredLength,
                                     Instant expiresAt, String credentials,
                                     String tokenForLogging) throws IOException {
            File tmp = Files.createTempFile("mapepire-blob-", ".tmp").toFile();
            tmp.deleteOnExit();

            // Latch starts at 1 — spool thread counts it down when done
            CountDownLatch ready = new CountDownLatch(1);
            BlobEntry entry = new BlobEntry(null, tmp, declaredLength, expiresAt, credentials, ready);

            Thread spoolThread = new Thread(() -> {
                try (FileOutputStream fos = new FileOutputStream(tmp);
                     InputStream in = blob.getBinaryStream()) {
                    byte[] buf = new byte[65536];
                    int read;
                    long total = 0;
                    while ((read = in.read(buf)) != -1) {
                        fos.write(buf, 0, read);
                        total += read;
                    }
                    Tracer.info("BlobStore: spool complete for token " + tokenForLogging
                            + " (" + total + " bytes)");
                } catch (IOException | SQLException e) {
                    Tracer.err(e);
                    entry.m_spoolError = (e instanceof IOException)
                            ? (IOException) e
                            : new IOException("JDBC Blob read failed: " + e.getMessage(), e);
                } finally {
                    try { blob.free(); } catch (SQLException ignored) {}
                    ready.countDown();
                }
            }, "BlobStore-spool-" + tokenForLogging);
            spoolThread.setDaemon(true);
            spoolThread.start();

            return entry;
        }

        // -- accessors --------------------------------------------------------

        /**
         * Open an InputStream over this entry's data.
         *
         * <p>If the blob is still being spooled to disk, this method blocks
         * until the spool finishes or the TTL elapses (whichever comes first).
         * If the spool fails or the wait times out, an {@link IOException} is
         * thrown and the caller should invoke {@link #cleanup()} in a
         * {@code finally} block.</p>
         *
         * <p>Caller is responsible for closing the returned stream.</p>
         */
        public InputStream openStream() throws IOException {
            if (m_ready.getCount() > 0) {
                // Still spooling — wait up to the remaining TTL
                long waitSeconds = Math.max(1L,
                        expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
                try {
                    boolean completed = m_ready.await(waitSeconds, TimeUnit.SECONDS);
                    if (!completed) {
                        throw new IOException("Blob spool timed out after " + waitSeconds + "s");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for blob spool", e);
                }
            }
            if (m_spoolError != null) {
                throw new IOException("Blob spool failed: " + m_spoolError.getMessage(), m_spoolError);
            }
            if (m_bytes != null) {
                return new ByteArrayInputStream(m_bytes);
            }
            return new FileInputStream(m_file);
        }

        /** Delete the backing temp file if one exists. */
        public void cleanup() {
            if (m_file != null && m_file.exists()) {
                m_file.delete();
            }
        }
    }
}
