package com.github.ibm.mapepire;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages per-connection trace contexts for daemon mode.
 * Each WebSocket connection gets its own isolated trace buffer.
 * 
 * This ensures:
 * - Per-connection log isolation (no cross-session log leakage)
 * - Thread-safe trace operations in multi-client scenarios
 * - Automatic cleanup of expired traces
 * - Support for daemon mode tracing (previously disabled)
 */
public class ConnectionTraceContext {
    
    private static final ConcurrentHashMap<String, TraceBuffer> traceContexts = new ConcurrentHashMap<>();
    private static final long DEFAULT_EXPIRY_TIME = 24 * 60 * 60 * 1000; // 24 hours
    
    /**
     * Represents a per-connection trace buffer with isolated log entries.
     */
    public static class TraceBuffer {
        private final Tracer.InMemCache<Tracer.Entry> buffer;
        private final String connectionId;
        private final long createdAt;
        
        /**
         * Create a new trace buffer for a connection.
         * 
         * @param connectionId unique identifier for the connection
         * @param bufferCapacity maximum number of trace entries to retain
         */
        public TraceBuffer(String connectionId, int bufferCapacity) {
            this.connectionId = connectionId;
            this.createdAt = System.currentTimeMillis();
            this.buffer = new Tracer.InMemCache<>(bufferCapacity);
        }
        
        /**
         * Add a trace entry to this connection's buffer.
         * 
         * @param entry the trace entry to add
         */
        public synchronized void add(Tracer.Entry entry) {
            buffer.add(entry);
        }
        
        /**
         * Get all trace entries as HTML.
         * 
         * @return formatted HTML containing all trace entries
         */
        public synchronized StringBuffer getAsHtml() {
            StringBuffer buf = new StringBuffer();
            buf.append("<html><body bgcolor=\"white\">\n\n");
            buf.append("<h3>Connection: ").append(connectionId).append("</h3>\n");
            buf.append("<p>Created: ").append(new java.util.Date(createdAt)).append("</p>\n");
            
            Collection<Tracer.Entry> entries = buffer.getEntries();
            if (entries.isEmpty()) {
                buf.append("<p><em>No trace entries</em></p>\n");
            } else {
                for (Tracer.Entry entry : entries) {
                    buf.append(entry.asHtml());
                    buf.append("\n");
                }
            }
            
            buf.append("</body></html>");
            return buf;
        }
        
        /**
         * Get all trace entries as plain text (for debugging).
         * 
         * @return plain text containing all trace entries
         */
        public synchronized StringBuffer getAsPlainText() {
            StringBuffer buf = new StringBuffer();
            buf.append("Connection ID: ").append(connectionId).append("\n");
            buf.append("Created: ").append(new java.util.Date(createdAt)).append("\n");
            buf.append("=====================================\n");
            
            for (Tracer.Entry entry : buffer.getEntries()) {
                buf.append("[").append(entry.getEventType()).append("] ");
                buf.append(entry.getFormattedDate()).append(": ");
                buf.append(entry.getDataAsString()).append("\n");
            }
            
            return buf;
        }
        
        /**
         * Check if this trace buffer has expired.
         * 
         * @param maxAge maximum age in milliseconds
         * @return true if buffer is older than maxAge
         */
        public boolean isExpired(long maxAge) {
            return System.currentTimeMillis() - createdAt > maxAge;
        }
        
        /**
         * Get the connection ID for this trace buffer.
         * 
         * @return unique connection identifier
         */
        public String getConnectionId() {
            return connectionId;
        }
        
        /**
         * Get the creation timestamp.
         * 
         * @return milliseconds since creation
         */
        public long getCreatedAt() {
            return createdAt;
        }
    }
    
    /**
     * Get or create a trace buffer for a specific connection.
     * 
     * @param connectionId unique identifier for the connection
     * @return the trace buffer for this connection
     */
    public static TraceBuffer getOrCreate(String connectionId) {
        return traceContexts.computeIfAbsent(connectionId, id -> 
            new TraceBuffer(id, 100) // 100 entries per connection
        );
    }
    
    /**
     * Get an existing trace buffer without creating one.
     * 
     * @param connectionId unique identifier for the connection
     * @return the trace buffer, or null if it doesn't exist
     */
    public static TraceBuffer get(String connectionId) {
        return traceContexts.get(connectionId);
    }
    
    /**
     * Remove and cleanup a trace buffer for a connection.
     * 
     * @param connectionId unique identifier for the connection
     * @return the removed trace buffer, or null if it didn't exist
     */
    public static TraceBuffer remove(String connectionId) {
        return traceContexts.remove(connectionId);
    }
    
    /**
     * Get trace data as HTML for a specific connection.
     * 
     * @param connectionId unique identifier for the connection
     * @return HTML formatted trace data
     */
    public static StringBuffer getTraceDataAsHtml(String connectionId) {
        TraceBuffer buffer = traceContexts.get(connectionId);
        if (buffer == null) {
            StringBuffer buf = new StringBuffer();
            buf.append("<html><body bgcolor=\"white\">\n");
            buf.append("<p><em>No trace data found for connection: ").append(connectionId).append("</em></p>\n");
            buf.append("</body></html>");
            return buf;
        }
        return buffer.getAsHtml();
    }
    
    /**
     * Get trace data as plain text for a specific connection.
     * 
     * @param connectionId unique identifier for the connection
     * @return plain text formatted trace data
     */
    public static StringBuffer getTraceDataAsPlainText(String connectionId) {
        TraceBuffer buffer = traceContexts.get(connectionId);
        if (buffer == null) {
            StringBuffer buf = new StringBuffer();
            buf.append("No trace data found for connection: ").append(connectionId).append("\n");
            return buf;
        }
        return buffer.getAsPlainText();
    }
    
    /**
     * Cleanup expired trace buffers to prevent memory leaks.
     * Should be called periodically (e.g., every hour).
     * 
     * @return number of buffers cleaned up
     */
    public static int cleanup() {
        return cleanup(DEFAULT_EXPIRY_TIME);
    }
    
    /**
     * Cleanup trace buffers older than maxAge.
     * 
     * @param maxAge maximum age in milliseconds for a buffer
     * @return number of buffers cleaned up
     */
    public static int cleanup(long maxAge) {
        int cleanedCount = 0;
        for (String connectionId : traceContexts.keySet()) {
            TraceBuffer buffer = traceContexts.get(connectionId);
            if (buffer != null && buffer.isExpired(maxAge)) {
                traceContexts.remove(connectionId);
                cleanedCount++;
                Tracer.globalInfo("Cleaned up expired trace buffer for connection: " + connectionId);
            }
        }
        return cleanedCount;
    }
    
    /**
     * Get the number of active trace buffers.
     * 
     * @return count of active connections with trace data
     */
    public static int getActiveConnectionCount() {
        return traceContexts.size();
    }
    
    /**
     * Clear all trace buffers (use with caution).
     */
    public static void clearAll() {
        traceContexts.clear();
    }
}