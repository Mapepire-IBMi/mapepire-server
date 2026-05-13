package com.github.ibm.mapepire;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

import com.ibm.as400.access.Trace;

public class Tracer {
    public enum Dest {
        FILE,
        IN_MEM
    }

    public enum TraceLevel {
        OFF, // off
        ON, // all except datastream
        ERRORS, // errors only
        DATASTREAM, // all including data stream
        INPUT_AND_ERRORS // input data stream and errors
    }

    public enum EventType {
        INFO,
        WARN,
        DATASTREAM_IN,
        DATASTREAM_OUT,
        ERR;

        public boolean isLoggedAt(TraceLevel _l) {
            switch (_l) {
                case OFF:
                    return false;
                case ON:
                    return this != DATASTREAM_IN && this != DATASTREAM_OUT;
                case ERRORS:
                    return this == ERR;
                case DATASTREAM:
                    return true;
                case INPUT_AND_ERRORS:
                    return this == DATASTREAM_IN || this == ERR;
                default:
                    return false;
            }
        }

        public String getHtmlColor() {
            switch (this) {
                case ERR:
                    return "tomato";
                case WARN:
                    return "orange";
                case DATASTREAM_IN:
                    return "DarkSlateGrey";
                case DATASTREAM_OUT:
                    return "DarkSlateBlue";
                default:
                    return "black";
            }
        }
    }

    public static class Entry {
        private final Object m_data;
        private final Date m_date;
        private final EventType m_type;
        private String m_html = null;

        public Entry(EventType _type, Object _data) {
            m_date = new Date();
            m_type = _type;
            m_data = _data;
        }

        public String asHtml() {
            if (null != m_html) {
                return m_html;
            }
            String rawTraceData = getRawTraceString();
            String ret = "\n<hr>\n";
            ret += String.format("<b>[%s]: </b><i>%s</i>\n", m_type.name(), getDateFormatter().format(m_date));
            ret += String.format("<font color=\"%s\">\n<blockquote>\n<pre>\n%s\n</pre>\n</blockquote>\n</font>",
                    m_type.getHtmlColor(), rawTraceData);
            return m_html = ret;
        }

        private String getRawTraceString() {
            if (m_data instanceof Throwable) {
                return Tracer.exceptionToStackTrace((Throwable) m_data);
            } else {
                return "" + m_data;
            }
        }
        public EventType getEventType() {
            return m_type;
        }

        public String getFormattedDate() {
            return getDateFormatter().format(m_date);
        }

        public String getDataAsString() {
            return getRawTraceString();
        }
    }

    private static Tracer s_instance = new Tracer();
    private static String s_pseudoPid = ("" + Math.random()).replace(".", "").replace("0", "");

    private static DateFormat s_dateFormatter = null;

    public static String exceptionToStackTrace(Throwable m_data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream stringStream = new PrintStream(baos, false);
        ((Throwable) m_data).printStackTrace(stringStream);
        stringStream.close();
        return new String(baos.toByteArray());
    }

    /**
     * Get the global Tracer instance for application-wide logging.
     * For per-connection tracing in daemon mode, use getNew(String connectionId) instead.
     *
     * @return the global Tracer instance
     */
    public static Tracer getGlobalTracer() {
        return s_instance;
    }

    /**
     * @deprecated Use getGlobalTracer() instead for clarity
     */
    @Deprecated
    public static Tracer get() {
        return getGlobalTracer();
    }

    /**
     * Create a new Tracer instance for per-connection tracing in daemon mode.
     * This ensures trace isolation between different client connections.
     *
     * @param connectionId unique identifier for the connection
     * @return a new Tracer instance configured for this connection
     */
    public static Tracer getNew(String connectionId) {
        Tracer tracer = new Tracer();
        tracer.m_connectionId = connectionId;
        return tracer;
    }

    /**
     * Log an info message to the global tracer (static method for backward compatibility).
     * For per-connection logging, create a Tracer instance via getNew() and call info() on it.
     *
     * @param _data the data to log
     */
    public static void info(Object _data) {
        getGlobalTracer().logInfo(_data);
    }

    /**
     * Log a warning message to the global tracer (static method for backward compatibility).
     * For per-connection logging, create a Tracer instance via getNew() and call warn() on it.
     *
     * @param _data the data to log
     */
    public static void warn(Object _data) {
        getGlobalTracer().logWarn(_data);
    }

    /**
     * Log an error message to the global tracer (static method for backward compatibility).
     * For per-connection logging, create a Tracer instance via getNew() and call err() on it.
     *
     * @param _data the data to log
     */
    public static void err(Object _data) {
        getGlobalTracer().logErr(_data);
    }

    /**
     * Log incoming datastream to the global tracer (static method for backward compatibility).
     * For per-connection logging, create a Tracer instance via getNew() and call datastreamIn() on it.
     *
     * @param _data the data to log
     */
    public static void datastreamIn(Object _data) {
        getGlobalTracer().logDatastreamIn(_data);
    }

    /**
     * Log outgoing datastream to the global tracer (static method for backward compatibility).
     * For per-connection logging, create a Tracer instance via getNew() and call datastreamOut() on it.
     *
     * @param _data the data to log
     */
    public static void datastreamOut(Object _data) {
        getGlobalTracer().logDatastreamOut(_data);
    }

    /**
     * Instance method: Log an info message to this Tracer instance.
     * Use this on per-connection tracers created via getNew().
     *
     * @param _data the data to log
     */
    public void logInfo(Object _data) {
        Trace(EventType.INFO, _data);
    }

    /**
     * Instance method: Log a warning message to this Tracer instance.
     * Use this on per-connection tracers created via getNew().
     *
     * @param _data the data to log
     */
    public void logWarn(Object _data) {
        Trace(EventType.WARN, _data);
    }

    /**
     * Instance method: Log an error message to this Tracer instance.
     * Use this on per-connection tracers created via getNew().
     *
     * @param _data the data to log
     */
    public void logErr(Object _data) {
        Trace(EventType.ERR, _data);
    }

    /**
     * Instance method: Log incoming datastream to this Tracer instance.
     * Use this on per-connection tracers created via getNew().
     *
     * @param _data the data to log
     */
    public void logDatastreamIn(Object _data) {
        Trace(EventType.DATASTREAM_IN, _data);
    }

    /**
     * Instance method: Log outgoing datastream to this Tracer instance.
     * Use this on per-connection tracers created via getNew().
     *
     * @param _data the data to log
     */
    public void logDatastreamOut(Object _data) {
        Trace(EventType.DATASTREAM_OUT, _data);
    }

    private static DateFormat getDateFormatter() {
        if (null != s_dateFormatter) {
            return s_dateFormatter;
        }
        return s_dateFormatter = new SimpleDateFormat("yyyy-MM-dd'.'kk.mm.ss.SSS");
    }

    public static class InMemCache<T> {
        private final AtomicInteger m_ctr = new AtomicInteger(0);
        private final int m_capacity;
        private final LinkedHashMap<Integer, T> m_data;

        public InMemCache(int _capacity) {
            m_capacity = _capacity;
            m_data = new LinkedHashMap<Integer, T>() {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<Integer, T> eldest) {
                    return m_capacity < size();
                }
            };
        }

        public void add(T _data) {
            m_data.put(m_ctr.incrementAndGet(), _data);
        }

        public Collection<T> getEntries() {
            return m_data.values();
        }
    }

    private InMemCache<Entry> m_inMem = new InMemCache<Entry>(100);

    private InMemCache<String> m_jtOpenInMem = new InMemCache<String>(16 * 1024);

    private Dest m_dest = Dest.IN_MEM;

    private OutputStreamWriter m_fileWriter = null;
    private PrintWriter m_jtOpenFileWriter = null;

    private File m_destFile = null;
    private File m_jtOpenDestFile = null;

    private TraceLevel m_traceLevel = TraceLevel.INPUT_AND_ERRORS;
    private TraceLevel m_jtOpenTraceLevel = TraceLevel.OFF;
    private Dest m_jtopenDest = Dest.IN_MEM;

    private String m_connectionId = null; // ✅ NEW: For per-connection tracing in daemon mode

    private Tracer() {
        PrintWriter jt400PrintWriter = new PrintWriter(new Writer() {
            @Override
            public void write(char[] _cbuf, int _off, int _len) throws IOException {
                String data = new String(_cbuf, _off, _len);
                if (Dest.IN_MEM == m_jtopenDest) {
                    m_jtOpenInMem.add(data);
                    return;
                }
                if (null == m_jtOpenFileWriter) {
                    try {
                        m_jtOpenFileWriter = new PrintWriter(getJtOpenFile(), "UTF-8");
                    } catch (Exception e) {
                        e.printStackTrace();
                        m_jtopenDest = Dest.IN_MEM;
                        m_jtOpenInMem.add(data);
                    }
                }
                m_jtOpenFileWriter.write(data);
                if (data.contains("\n")) {
                    m_jtOpenFileWriter.flush();
                }
            }

            @Override
            public void flush() throws IOException {
                if (null != m_jtOpenFileWriter) {
                    m_jtOpenFileWriter.flush();
                }
            }

            @Override
            public void close() throws IOException {
                if (null != m_jtOpenFileWriter) {
                    m_jtOpenFileWriter.close();
                    m_jtOpenFileWriter = null;
                }
            }
        });
        try {
            Trace.setPrintWriter(jt400PrintWriter);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the current connection ID for this Tracer instance.
     *
     * @return the connection ID, or null if this is the global tracer
     */
    public String getConnectionId() {
        return m_connectionId;
    }

    public Tracer setTraceLevel(TraceLevel _l) {
    // ✅ FIXED: Allow tracing in daemon mode (removed single mode check)
    m_traceLevel = _l;
    return this;
    }

    public Tracer setJtOpenTraceLevel(TraceLevel _l) {
        // ✅ FIXED: Allow JtOpen tracing in daemon mode (removed single mode check)
        switch (_l) {
            case OFF:
                Trace.setTraceOn(false);
                Trace.setTraceAllOn(false);
                Trace.setTraceDatastreamOn(false);
                break;
            case ON:
                Trace.setTraceOn(true);
                Trace.setTraceAllOn(true);
                Trace.setTraceDatastreamOn(false);
                break;
            case DATASTREAM:
                Trace.setTraceOn(true);
                Trace.setTraceAllOn(true);
                Trace.setTraceDatastreamOn(true);
                break;
            case ERRORS:
                Trace.setTraceOn(true);
                Trace.setTraceAllOn(false);
                Trace.setTraceErrorOn(true);
                break;
        }
        return this;
    }

    public Tracer setDest(Dest _dest) {
  // ✅ FIXED: Allow destination changes in daemon mode (removed single mode check)
        if (m_dest == _dest) {
            return this;
        }
        if (Dest.FILE == m_dest && null != m_fileWriter) {
            try {
                m_fileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            m_fileWriter = null;
        }
        m_dest = _dest;
        return this;
    }

    public Tracer setJtOpenDest(Dest _dest) throws FileNotFoundException, UnsupportedEncodingException, IOException {
       // ✅ FIXED: Allow destination changes in daemon mode (removed single mode check)
        if (m_jtopenDest == _dest) {
            return this;
        }
        if (Dest.FILE == m_dest && null != m_jtOpenFileWriter) {
            m_jtOpenFileWriter.flush();
            m_jtOpenFileWriter.close();
            m_jtOpenFileWriter = null;
        }
        m_jtopenDest = _dest;
        return this;
    }

    public String getDestString() throws IOException {
    // ✅ FIXED: Return actual destination instead of "unknown" in daemon mode
        switch (m_dest) {
            case FILE:
                return getFile().getAbsolutePath();
            case IN_MEM:
                return "IN_MEM";
            default:
                return "unknown";
        }
    }

    public String getJtOpenDestString() throws IOException {
    // ✅ FIXED: Return actual destination instead of "unknown" in daemon mode
        switch (m_dest) {
            case FILE:
                return getJtOpenFile().getAbsolutePath();
            case IN_MEM:
                return "IN_MEM";
            default:
                return "unknown";
        }
    }

    public TraceLevel getTraceLevel() {
        return m_traceLevel;
    }

    public TraceLevel getJtOpenTraceLevel() {
        return m_jtOpenTraceLevel;
    }

    public StringBuffer getRawData() throws IOException {
        // ✅ FIXED: Support daemon mode per-connection trace retrieval
        if (!MapepireServer.isSingleMode() && m_connectionId != null) {
            return ConnectionTraceContext.getTraceDataAsHtml(m_connectionId);
    }

    // Single mode behavior (unchanged)
    StringBuffer buf = new StringBuffer();
        if (Dest.IN_MEM == m_dest) {
            buf.append("<html><body bgcolor=\"white\">\n\n");
            synchronized (m_inMem) {
                for (Entry l : m_inMem.getEntries()) {
                    buf.append(l.asHtml());
                    buf.append("\n");
                }
            }
        } else {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(getFile()), "UTF-8"))) {
                String lineString = null;
                while (null != (lineString = reader.readLine())) {
                    buf.append(lineString);
                    buf.append("\n");
                }
            }
        }
        buf.append("</body></html>");
        return buf;
    }

    public StringBuffer getJtOpenRawData() throws UnsupportedEncodingException, FileNotFoundException, IOException {
        if(!MapepireServer.isSingleMode()) {
            return new StringBuffer("<prohibited>");
        }
        StringBuffer buf = new StringBuffer();
        if (Dest.IN_MEM == m_jtopenDest) {
            synchronized (m_jtOpenInMem) {
                for (String l : m_jtOpenInMem.getEntries()) {
                    buf.append(l);
                }
            }
        } else {
            Trace.getPrintWriter().flush();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(getJtOpenFile()), "UTF-8"))) {
                String lineString = null;
                while (null != (lineString = reader.readLine())) {
                    buf.append(lineString);
                    buf.append("\r\n");
                }
            }
        }
        return buf;
    }

    private Tracer Trace(EventType _t, Object _data) {
        // TODO: audit fallback cases with use of printStackTrace() throughout
        if ((_data instanceof Throwable) && !System.getProperty("os.name", "").contains("400")) {
            ((Throwable) _data).printStackTrace();
        }
        if (!_t.isLoggedAt(m_traceLevel)) {
            return this;
        }

        Entry entry = new Entry(_t, _data);

        // ✅ NEW: Daemon mode - use per-connection context
        if (!MapepireServer.isSingleMode() && m_connectionId != null) {
            ConnectionTraceContext.getOrCreate(m_connectionId).add(entry);
            return this;
        }

        // Existing single mode behavior
        if (Dest.IN_MEM == m_dest) {
            m_inMem.add(entry);
            return this;
        }
        if (null == m_fileWriter) {
            try {
                m_fileWriter = new OutputStreamWriter(new FileOutputStream(getFile(), true), "UTF-8");
                m_fileWriter.write("<html><body bgcolor=\"white\">\n\n");
                m_fileWriter.write(
                        new Entry(EventType.INFO,
                                String.format("Tracing enabled to file '%s'", m_destFile.getAbsolutePath()))
                                .asHtml());
            } catch (Exception e) {
                e.printStackTrace();
                m_dest = Dest.IN_MEM;
                m_inMem.add(new Entry(_t, _data));
                return this;
            }
        }
        try {
            m_fileWriter.write(new Entry(_t, _data).asHtml());
            m_fileWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this;
    }

    private File getFile() throws IOException {
        if (null != m_destFile) {
            return m_destFile;
        }
        try {
            URL location = Tracer.class.getProtectionDomain().getCodeSource().getLocation();
            File f = new File(location.toURI());
            File dir = f.isDirectory() ? f : f.getParentFile();
            String dateStr = getDateFormatter().format(new Date());
            String fileName = String.format("vsc-%s-%s.html", dateStr, s_pseudoPid);
            File ret = m_destFile = new File(dir, fileName);
            ret.createNewFile();
            return m_destFile = ret;
        } catch (Exception e) {
            return m_destFile = File.createTempFile("VSCode", ".html");
        }
    }

    private File getJtOpenFile() throws IOException {
        if (null != m_jtOpenDestFile) {
            return m_jtOpenDestFile;
        }
        try {
            URL location = Tracer.class.getProtectionDomain().getCodeSource().getLocation();
            File f = new File(location.toURI());
            File dir = f.isDirectory() ? f : f.getParentFile();
            String dateStr = getDateFormatter().format(new Date());
            String fileName = String.format("vsc-jtopen-%s-%s.txt", dateStr, s_pseudoPid);
            File ret = new File(dir, fileName);
            ret.createNewFile();
            return m_jtOpenDestFile = ret;
        } catch (Exception e) {
            return m_jtOpenDestFile = File.createTempFile("VSCode-jtopen", ".txt");
        }
    }
}
