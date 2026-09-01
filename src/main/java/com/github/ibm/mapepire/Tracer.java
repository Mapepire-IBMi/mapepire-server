package com.github.ibm.mapepire;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.ibm.as400.access.Trace;
import com.ibm.ibmi_util.SystemNativeUtils;

public class Tracer {
    public enum Dest {
        FILE, IN_MEM, DEV_NULL_OR_STDERR
    }

    public enum TraceLevel {
        OFF, // off
        ON, // all except datastream
        ERRORS, // errors only
        DATASTREAM, // all including data stream
        INPUT_AND_ERRORS // input data stream and errors
    }

    public enum EventType {
        INFO, WARN, DATASTREAM_IN, DATASTREAM_OUT, ERR;

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
            ret += String.format("<font color=\"%s\">\n<blockquote>\n<pre>\n%s\n</pre>\n</blockquote>\n</font>", m_type.getHtmlColor(), rawTraceData);
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

    private static final String GLOBAL_CONNECTION_ID = "global";

    // The global tracer is explicitly protected (that is, there's no static "getter" for it)
    // as we don't want to expose full control of the global tracer.
    private static Tracer s_globalTracer = new Tracer(true);
    private static String s_pseudoPid = ("" + Math.random()).replace(".", "").replace("0", "");

    private static DateFormat s_dateFormatter = null;
    private static AtomicLong s_connectionIdGenerator = new AtomicLong(0);

    public static String exceptionToStackTrace(Throwable m_data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream stringStream = new PrintStream(baos, false);
        ((Throwable) m_data).printStackTrace(stringStream);
        stringStream.close();
        return new String(baos.toByteArray());
    }

    /**
     * Create a new Tracer instance for per-connection tracing in daemon mode.
     * This ensures trace isolation between different client connections.
     *
     * @param connectionId
     *            unique identifier for the connection
     * @return a new Tracer instance configured for this connection
     */
    public static Tracer getNew() {
        return new Tracer(false);
    }

    /**
     * Log an info message to the global tracer (static method for backward compatibility).
     * For per-connection logging, create a Tracer instance via getNew() and call info() on it.
     *
     * @param _data
     *            the data to log
     */
    public static void globalInfo(Object _data) {
        s_globalTracer.logInfo(_data);
    }

    /**
     * Log a warning message to the global tracer (static method for backward compatibility).
     * For per-connection logging, create a Tracer instance via getNew() and call warn() on it.
     *
     * @param _data
     *            the data to log
     */
    public static void globalWarn(Object _data) {
        s_globalTracer.logWarn(_data);
    }

    /**
     * Log an error message to the global tracer (static method for backward compatibility).
     * For per-connection logging, create a Tracer instance via getNew() and call err() on it.
     *
     * @param _data
     *            the data to log
     */
    public static void globalErr(Object _data) {
        s_globalTracer.logErr(_data);
    }

    /**
     * Log incoming datastream to the global tracer (static method for backward compatibility).
     * For per-connection logging, create a Tracer instance via getNew() and call datastreamIn() on it.
     *
     * @param _data
     *            the data to log
     */
    public static void globalDatastreamIn(Object _data) {
        s_globalTracer.logDatastreamIn(_data);
    }

    /**
     * Log outgoing datastream to the global tracer (static method for backward compatibility).
     * For per-connection logging, create a Tracer instance via getNew() and call datastreamOut() on it.
     *
     * @param _data
     *            the data to log
     */
    public static void globalDatastreamOut(Object _data) {
        s_globalTracer.logDatastreamOut(_data);
    }

    /**
     * Instance method: Log an info message to this Tracer instance.
     * Use this on per-connection tracers created via getNew().
     *
     * @param _data
     *            the data to log
     */
    public void logInfo(Object _data) {
        Trace(EventType.INFO, _data);
    }

    /**
     * Instance method: Log a warning message to this Tracer instance.
     * Use this on per-connection tracers created via getNew().
     *
     * @param _data
     *            the data to log
     */
    public void logWarn(Object _data) {
        Trace(EventType.WARN, _data);
    }

    /**
     * Instance method: Log an error message to this Tracer instance.
     * Use this on per-connection tracers created via getNew().
     *
     * @param _data
     *            the data to log
     */
    public void logErr(Object _data) {
        Trace(EventType.ERR, _data);
    }

    /**
     * Instance method: Log incoming datastream to this Tracer instance.
     * Use this on per-connection tracers created via getNew().
     *
     * @param _data
     *            the data to log
     */
    public void logDatastreamIn(Object _data) {
        Trace(EventType.DATASTREAM_IN, _data);
    }

    /**
     * Instance method: Log outgoing datastream to this Tracer instance.
     * Use this on per-connection tracers created via getNew().
     *
     * @param _data
     *            the data to log
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

    private Dest m_dest = Dest.IN_MEM;

    private OutputStreamWriter m_fileWriter = null;

    private File m_destFile = null;

    private TraceLevel m_traceLevel;

    private final String m_connectionId; // ✅ NEW: For per-connection tracing in daemon mode

    private final boolean m_isGlobal;

    private Tracer(boolean _isGlobal) {
        m_connectionId = _isGlobal ? GLOBAL_CONNECTION_ID : ("" + s_connectionIdGenerator.incrementAndGet());
        m_isGlobal = _isGlobal;
        m_traceLevel = _isGlobal ? TraceLevel.ON : TraceLevel.INPUT_AND_ERRORS;
        m_dest = _isGlobal ? Dest.DEV_NULL_OR_STDERR : Dest.IN_MEM;
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

    public String getDestString() throws IOException {
        switch (m_dest) {
            case FILE:
                return getFile().getAbsolutePath();
            case IN_MEM:
                return "IN_MEM";
            default:
                return "unknown";
        }
    }

    public TraceLevel getTraceLevel() {
        return m_traceLevel;
    }

    public StringBuffer getRawData() throws IOException {
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
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(getFile()), "UTF-8"))) {
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

    private Tracer Trace(EventType _t, Object _data) {
        // TODO: audit fallback cases with use of printStackTrace() throughout
        if ((_data instanceof Throwable) && !System.getProperty("os.name", "").contains("400")) {
            ((Throwable) _data).printStackTrace();
        }
        if (!_t.isLoggedAt(m_traceLevel)) {
            return this;
        }
        if (null == _data) {
            return this;
        }

        if (m_isGlobal) {
            final String simpleData = String.format("%s: %s", _t.name(), _data.toString());
            if (SystemNativeUtils.isNativeLoaded()) {
                SystemNativeUtils.writeToJobLog(simpleData);
                return this;
            } else if (m_dest == Dest.DEV_NULL_OR_STDERR) {
                System.err.println(simpleData);
                return this;
            }
        }

        if (Dest.DEV_NULL_OR_STDERR == m_dest) {
            return this;
        }

        Entry entry = new Entry(_t, _data);

        if (Dest.IN_MEM == m_dest) {
            m_inMem.add(entry);
            return this;
        }
        if (null == m_fileWriter) {
            try {
                m_fileWriter = new OutputStreamWriter(new FileOutputStream(getFile(), true), "UTF-8");
                m_fileWriter.write("<html><body bgcolor=\"white\">\n\n");
                m_fileWriter.write(new Entry(EventType.INFO, String.format("Tracing enabled to file '%s'", m_destFile.getAbsolutePath())).asHtml());
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

    private synchronized File getFile() throws IOException {
        if (null != m_destFile) {
            return m_destFile;
        }
        String dateStr = getDateFormatter().format(new Date());
        String filePrefix = String.format("vscode-%s-%s-", dateStr, s_pseudoPid);

        File logDir = new File("/QOpenSys/QIBM/UserData/AI/mapepire/logs");
        final File ret;
        if (logDir.isDirectory() && logDir.canWrite()) {
            ret = File.createTempFile(filePrefix, ".html", logDir);
        } else if ("QUSER".equalsIgnoreCase(System.getProperty("user.name"))) {
            logDir.mkdirs();
            Process p = Runtime.getRuntime().exec(new String[] { "/QOpenSys/usr/bin/chmod", "600", logDir.getAbsolutePath() });
            if (0 == p.exitValue()) {
                ret = File.createTempFile(filePrefix, ".html", logDir);
            } else {
                ret = File.createTempFile(filePrefix, ".html");
            }
        } else {
            ret = File.createTempFile(filePrefix, ".html");
        }
        return m_destFile = ret;
    }

    public static Tracer getGlobalTracer() {
        return s_globalTracer;
    }

    public static String getJtOpenComponentStatusString() {
        //@formatter:off
        final String components = String.format("CONVERSION:%B,DATASTREAM:%B,DIAGNOSTIC:%B,ERROR:%B,INFO:%B,PCML:%B,PROXY:%B,THREAD:%B", 
                Trace.isTraceConversionOn(),
                Trace.isTraceDatastreamOn(),
                Trace.isTraceDiagnosticOn(),
                Trace.isTraceErrorOn(),
                Trace.isTraceInformationOn(),
                Trace.isTracePCMLOn(),
                Trace.isTraceProxyOn(),
                Trace.isTraceThreadOn()
            );
        String ret = String.format(
        "Java Toolbox Components status: %s", 
                components);
        //@formatter:on
        return ret;
    }

    public static String getJtOpenStatusString() {
        //@formatter:off
        final String components = String.format("CONVERSION:%B,DATASTREAM:%B,DIAGNOSTIC:%B,ERROR:%B,INFO:%B,PCML:%B,PROXY:%B,THREAD:%B", 
                Trace.isTraceConversionOn(),
                Trace.isTraceDatastreamOn(),
                Trace.isTraceDiagnosticOn(),
                Trace.isTraceErrorOn(),
                Trace.isTraceInformationOn(),
                Trace.isTracePCMLOn(),
                Trace.isTraceProxyOn(),
                Trace.isTraceThreadOn()
            );
        String ret = String.format("Java Toolbox tracing: %B,\n"+
        "Java Toolbox JDBC tracing: %B",
                Trace.isTraceOn(),
                Trace.isTraceJDBCOn()
                );
        //@formatter:on
        return ret;
    }

    public static String getJtOpenFileString() {
        return "Java toolbox trace file: " + Trace.getFileName();
    }
}
