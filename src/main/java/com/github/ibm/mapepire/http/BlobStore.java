package com.github.ibm.mapepire.http;

import com.github.ibm.mapepire.Tracer;

import java.io.*;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Singleton store for BLOB tokens.
 *
 * <p>BLOBs <= {@link #MEMORY_THRESHOLD_BYTES} are held in a {@code byte[]}.
 * BLOBs above that threshold are spooled to a JVM temp file so that heap
 * pressure is bounded regardless of BLOB size.</p>
 *
 * <p>Each entry carries the Basic-Auth credentials of the connection that
 * produced it so that {@link BlobServlet} can re-validate the caller.</p>
 *
 * <p>A background thread sweeps expired entries every 30 seconds.</p>
 */
public class BlobStore {

    // BLOBs larger than this are spooled to disk instead of held in memory
    public static final int MEMORY_THRESHOLD_BYTES = 1024 * 1024; // 1 MB

    // Default TTL in seconds — overridable via setconfig / env var
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
    // TTL configuration
    // -------------------------------------------------------------------------

    public static void setTtlSeconds(long ttl) {
        s_ttlSeconds = ttl;
    }

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
     * Store a BLOB from an {@link InputStream} of known length — avoids
     * materialising the full byte[] in heap for large BLOBs.
     */
    public String store(InputStream data, long length, String credentials) throws IOException {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(s_ttlSeconds);

        BlobEntry entry;
        if (length <= MEMORY_THRESHOLD_BYTES) {
            byte[] bytes = readAllBytes(data);
            entry = BlobEntry.ofBytes(bytes, expiresAt, credentials);
        } else {
            entry = BlobEntry.ofStream(data, expiresAt, credentials);
        }
        m_entries.put(token, entry);
        Tracer.info("BlobStore: stored token " + token + " size=" + length + " expires=" + expiresAt);
        return token;
    }

    // -------------------------------------------------------------------------
    // Retrieving blobs
    // -------------------------------------------------------------------------

    /**
     * Retrieve and <em>consume</em> a token. Returns {@code null} if the token
     * is unknown or has expired. The entry is removed immediately on retrieval
     * (single-use) and any temp file is deleted after streaming.
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
        // Exactly one of these is set
        private final byte[] m_bytes;
        private final File m_file;

        public final long size;
        public final Instant expiresAt;
        public final String credentials; // Base64 "user:pass"

        private BlobEntry(byte[] bytes, File file, long size, Instant expiresAt, String credentials) {
            this.m_bytes = bytes;
            this.m_file = file;
            this.size = size;
            this.expiresAt = expiresAt;
            this.credentials = credentials;
        }

        static BlobEntry ofBytes(byte[] bytes, Instant expiresAt, String credentials) {
            return new BlobEntry(bytes, null, bytes.length, expiresAt, credentials);
        }

        static BlobEntry ofFile(byte[] bytes, Instant expiresAt, String credentials) throws IOException {
            File tmp = Files.createTempFile("mapepire-blob-", ".tmp").toFile();
            tmp.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(bytes);
            }
            return new BlobEntry(null, tmp, bytes.length, expiresAt, credentials);
        }

        static BlobEntry ofStream(InputStream in, Instant expiresAt, String credentials) throws IOException {
            File tmp = Files.createTempFile("mapepire-blob-", ".tmp").toFile();
            tmp.deleteOnExit();
            long size = 0;
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                byte[] buf = new byte[65536];
                int read;
                while ((read = in.read(buf)) != -1) {
                    fos.write(buf, 0, read);
                    size += read;
                }
            }
            return new BlobEntry(null, tmp, size, expiresAt, credentials);
        }

        /**
         * Open an InputStream over this entry's data. Caller is responsible
         * for closing it. The temp file (if any) is deleted after the stream
         * is exhausted — callers should call {@link #cleanup()} in a finally
         * block if streaming fails.
         */
        public InputStream openStream() throws IOException {
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
