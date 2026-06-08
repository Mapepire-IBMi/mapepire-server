package com.github.ibm.mapepire.http;

import com.github.ibm.mapepire.Tracer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Streams a previously-stored BLOB to the caller.
 *
 * <p>URL pattern: {@code GET /blob/{token}}</p>
 *
 * <p>The caller must supply the same Basic-Auth credentials that were used
 * when the originating WebSocket connection ran the query. The token itself
 * is single-use and expires after the configured TTL.</p>
 *
 * <p>For large BLOBs the backing data may still be spooling to disk when this
 * request arrives. {@link BlobStore.BlobEntry#openStream()} will block until
 * the spool is complete before any response bytes are written, ensuring the
 * HTTP status code is always set correctly before the body begins.</p>
 */
public class BlobServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        // ---- Extract token from path  /blob/{token} -------------------------
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing blob token");
            return;
        }
        String token = pathInfo.substring(1); // strip leading '/'

        // ---- Validate Basic Auth --------------------------------------------
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            resp.setHeader("WWW-Authenticate", "Basic realm=\"mapepire\"");
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authorization required");
            return;
        }
        String suppliedCredentials = authHeader.substring(6).trim(); // raw Base64 "user:pass"

        // ---- Consume token --------------------------------------------------
        BlobStore.BlobEntry entry = BlobStore.getInstance().consume(token);
        if (entry == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Blob token not found or expired");
            return;
        }

        // ---- Verify credentials match ---------------------------------------
        if (!suppliedCredentials.equals(entry.credentials)) {
            // Put the entry back? No — single-use, deny and discard to prevent brute-force
            entry.cleanup();
            resp.setHeader("WWW-Authenticate", "Basic realm=\"mapepire\"");
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid credentials");
            return;
        }

        // ---- Wait for spool (if still in progress) and open stream ----------
        // openStream() blocks until the background spool thread finishes or the
        // TTL elapses. We must open the stream BEFORE committing any response
        // headers so that a spool failure can still be reported as a 500.
        InputStream in = null;
        try {
            in = entry.openStream();
        } catch (IOException e) {
            entry.cleanup();
            Tracer.err(e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Blob data unavailable: " + e.getMessage());
            return;
        }

        // ---- Stream bytes ---------------------------------------------------
        // Headers are committed only after openStream() succeeds, ensuring the
        // status code is always meaningful.
        resp.setContentType("application/octet-stream");
        resp.setHeader("Content-Length", String.valueOf(entry.size));
        resp.setHeader("Cache-Control", "no-store");

        try {
            OutputStream out = resp.getOutputStream();
            byte[] buf = new byte[65536];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            out.flush();
            Tracer.info("BlobServlet: streamed token " + token + " (" + entry.size + " bytes)");
        } finally {
            try { in.close(); } catch (IOException ignored) {}
            entry.cleanup();
        }
    }
}
