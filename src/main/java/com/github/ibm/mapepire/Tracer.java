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

/**
 * Diagnostic trace sink. A single global instance handles server-wide messages, and one instance per
 * client connection (obtained from {@link #getNew()}) keeps each client's diagnostics separate from
 * every other client's.
 */
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

        /**
         * Determine whether an event of this type is recorded at the given trace level.
         *
         * @param _traceLevel
         *            the trace level in effect
         * @return true if the event should be recorded
         */
        public boolean isLoggedAt(final TraceLevel _traceLevel) {
            switch (_traceLevel) {
                case OFF:
                    return false;
                case ON:
                    return DATASTREAM_IN != this && DATASTREAM_OUT != this;
                case ERRORS:
                    return ERR == this;
                case DATASTREAM:
                    return true;
                case INPUT_AND_ERRORS:
                    return DATASTREAM_IN == this || ERR == this;
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

    /**
     * A single traced event: its type, the moment it was traced, and the traced payload.
     */
    public static class Entry {
        private final Object m_data;
        private final Date m_date;
        private final EventType m_type;
        private String m_html = null;

        public Entry(final EventType _type, final Object _data) {
            m_date = new Date();
            m_type = _type;
            m_data = _data;
        }

        public String asHtml() {
            if (null != m_html) {
                return m_html;
            }
            final String rawTraceData = getRawTraceString();
            String ret = "\n<hr>\n";
            ret += String.format("<b>[%s]: </b><i>%s</i>\n", m_type.name(), getDateFormatter().format(m_date));
            ret += String.format("<font color=\"%s\">\n<blockquote>\n<pre>\n%s\n</pre>\n</blockquote>\n</font>", m_type.getHtmlColor(), rawTraceData);
            return m_html = ret;
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

        private String getRawTraceString() {
            if (m_data instanceof Throwable) {
                return Tracer.exceptionToStackTrace((Throwable) m_data);
            } else {
                return "" + m_data;
            }
        }
    }

    /**
     * A bounded, insertion-ordered buffer that discards its oldest element once it is full.
     *
     * @param <T>
     *            the type of element held in the cache
     */
    public static class InMemCache<T> {
        private final AtomicInteger m_ctr = new AtomicInteger(0);
        private final int m_capacity;
        private final LinkedHashMap<Integer, T> m_data;

        public InMemCache(final int _capacity) {
            m_capacity = _capacity;
            m_data = new LinkedHashMap<Integer, T>() {
                @Override
                protected boolean removeEldestEntry(final java.util.Map.Entry<Integer, T> _eldest) {
                    return m_capacity < size();
                }
            };
        }

        public void add(final T _data) {
            m_data.put(m_ctr.incrementAndGet(), _data);
        }

        public Collection<T> getEntries() {
            return m_data.values();
        }
    }

    private static final String GLOBAL_CONNECTION_ID = "global";

    // The global tracer is explicitly protected (that is, there's no static "getter" for it)
    // as we don't want to expose full control of the global tracer.
    private static final Tracer s_globalTracer = new Tracer(true);

    private static final AtomicLong s_connectionIdGenerator = new AtomicLong(0);

    private static DateFormat s_dateFormatter = null;

    public static String exceptionToStackTrace(final Throwable _throwable) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream stringStream = new PrintStream(baos, false);
        _throwable.printStackTrace(stringStream);
        stringStream.close();
        return new String(baos.toByteArray());
    }

    /**
     * Create a new Tracer instance for per-connection tracing in daemon mode.
     * This ensures trace isolation between different client connections.
     *
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
    public static void globalInfo(final Object _data) {
        s_globalTracer.logInfo(_data);
    }

    /**
     * Log a warning message to the global tracer (static method for backward compatibility).
     * For per-connection logging, create a Tracer instance via getNew() and call warn() on it.
     *
     * @param _data
     *            the data to log
     */
    public static void globalWarn(final Object _data) {
        s_globalTracer.logWarn(_data);
    }

    /**
     * Log an error message to the global tracer (static method for backward compatibility).
     * For per-connection logging, create a Tracer instance via getNew() and call err() on it.
     *
     * @param _data
     *            the data to log
     */
    public static void globalErr(final Object _data) {
        s_globalTracer.logErr(_data);
    }

    /**
     * Log incoming datastream to the global tracer (static method for backward compatibility).
     * For per-connection logging, create a Tracer instance via getNew() and call datastreamIn() on it.
     *
     * @param _data
     *            the data to log
     */
    public static void globalDatastreamIn(final Object _data) {
        s_globalTracer.logDatastreamIn(_data);
    }

    /**
     * Log outgoing datastream to the global tracer (static method for backward compatibility).
     * For per-connection logging, create a Tracer instance via getNew() and call datastreamOut() on it.
     *
     * @param _data
     *            the data to log
     */
    public static void globalDatastreamOut(final Object _data) {
        s_globalTracer.logDatastreamOut(_data);
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
        //@formatter:on
        return String.format("Java Toolbox Components status: %s", components);
    }

    public static String getJtOpenStatusString() {
        //@formatter:off
        final String ret = String.format("Java Toolbox tracing: %B,\n"+
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

    private static DateFormat getDateFormatter() {
        if (null != s_dateFormatter) {
            return s_dateFormatter;
        }
        return s_dateFormatter = new SimpleDateFormat("yyyy-MM-dd'.'kk.mm.ss.SSS");
    }

    private final String m_pseudoPid = ("" + Math.random()).replace(".", "").replace("0", "");

    private final InMemCache<Entry> m_inMem = new InMemCache<Entry>(100);

    private final String m_connectionId;

    private final boolean m_isGlobal;

    private Dest m_dest = Dest.IN_MEM;

    private OutputStreamWriter m_fileWriter = null;

    private File m_destFile = null;

    private TraceLevel m_traceLevel;

    private Tracer(final boolean _isGlobal) {
        m_connectionId = _isGlobal ? GLOBAL_CONNECTION_ID : ("" + s_connectionIdGenerator.incrementAndGet());
        m_isGlobal = _isGlobal;
        m_traceLevel = _isGlobal ? TraceLevel.ON : TraceLevel.INPUT_AND_ERRORS;
        m_dest = _isGlobal ? Dest.DEV_NULL_OR_STDERR : Dest.IN_MEM;
    }

    /**
     * Instance method: Log an info message to this Tracer instance.
     * Use this on per-connection tracers created via getNew().
     *
     * @param _data
     *            the data to log
     */
    public void logInfo(final Object _data) {
        trace(EventType.INFO, _data);
    }

    /**
     * Instance method: Log a warning message to this Tracer instance.
     * Use this on per-connection tracers created via getNew().
     *
     * @param _data
     *            the data to log
     */
    public void logWarn(final Object _data) {
        trace(EventType.WARN, _data);
    }

    /**
     * Instance method: Log an error message to this Tracer instance.
     * Use this on per-connection tracers created via getNew().
     *
     * @param _data
     *            the data to log
     */
    public void logErr(final Object _data) {
        trace(EventType.ERR, _data);
    }

    /**
     * Instance method: Log incoming datastream to this Tracer instance.
     * Use this on per-connection tracers created via getNew().
     *
     * @param _data
     *            the data to log
     */
    public void logDatastreamIn(final Object _data) {
        trace(EventType.DATASTREAM_IN, _data);
    }

    /**
     * Instance method: Log outgoing datastream to this Tracer instance.
     * Use this on per-connection tracers created via getNew().
     *
     * @param _data
     *            the data to log
     */
    public void logDatastreamOut(final Object _data) {
        trace(EventType.DATASTREAM_OUT, _data);
    }

    /**
     * Get the current connection ID for this Tracer instance.
     *
     * @return the connection ID, or null if this is the global tracer
     */
    public String getConnectionId() {
        return m_connectionId;
    }

    public Tracer setTraceLevel(final TraceLevel _traceLevel) {
        m_traceLevel = _traceLevel;
        return this;
    }

    public Tracer setDest(final Dest _dest) {
        if (_dest == m_dest) {
            return this;
        }
        if (Dest.FILE == m_dest && null != m_fileWriter) {
            try {
                m_fileWriter.close();
            } catch (final IOException _e) {
                _e.printStackTrace();
            }
            m_fileWriter = null;
        }
        m_dest = _dest;
        return this;
    }

    public String getDestString() throws IOException, InterruptedException {
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

    public StringBuffer getRawData() throws IOException, InterruptedException {
        final StringBuffer buf = new StringBuffer();
        if (Dest.IN_MEM == m_dest) {
            buf.append("<html><body bgcolor=\"white\">\n\n");
            synchronized (m_inMem) {
                for (final Entry entry : m_inMem.getEntries()) {
                    buf.append(entry.asHtml());
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

    private Tracer trace(final EventType _type, final Object _data) {
        // TODO: audit fallback cases with use of printStackTrace() throughout
        if ((_data instanceof Throwable) && !System.getProperty("os.name", "").contains("400")) {
            ((Throwable) _data).printStackTrace();
        }
        if (!_type.isLoggedAt(m_traceLevel)) {
            return this;
        }
        if (null == _data) {
            return this;
        }

        final Entry entry = new Entry(_type, _data);
        if (m_isGlobal) {
            final String simpleData = String.format("%s: %s", _type.name(), entry.getRawTraceString());
            if (SystemNativeUtils.isNativeLoaded()) {
                SystemNativeUtils.writeToJobLog(simpleData);
                return this;
            } else if (Dest.DEV_NULL_OR_STDERR == m_dest) {
                System.err.println(simpleData);
                return this;
            }
        }

        if (Dest.DEV_NULL_OR_STDERR == m_dest) {
            return this;
        }

        if (Dest.IN_MEM == m_dest) {
            m_inMem.add(entry);
            return this;
        }
        if (null == m_fileWriter) {
            try {
                m_fileWriter = new OutputStreamWriter(new FileOutputStream(getFile(), true), "UTF-8");
                m_fileWriter.write("<html><body bgcolor=\"white\">\n\n");
                m_fileWriter.write(new Entry(EventType.INFO, String.format("Tracing enabled to file '%s'", m_destFile.getAbsolutePath())).asHtml());
            } catch (final Exception _e) {
                _e.printStackTrace();
                m_dest = Dest.IN_MEM;
                m_inMem.add(new Entry(_type, _data));
                return this;
            }
        }
        try {
            m_fileWriter.write(new Entry(_type, _data).asHtml());
            m_fileWriter.flush();
        } catch (final IOException _e) {
            _e.printStackTrace();
        }
        return this;
    }

    private synchronized File getFile() throws IOException, InterruptedException {
        if (null != m_destFile) {
            return m_destFile;
        }
        final String dateStr = getDateFormatter().format(new Date());
        final String filePrefix = String.format("vscode-%s-%s-", dateStr, m_pseudoPid);

        final File logDir = new File("/QOpenSys/QIBM/UserData/AI/db_server/logs");
        final File ret;
        if (logDir.isDirectory() && logDir.canWrite()) {
            ret = File.createTempFile(filePrefix, ".html", logDir);
        } else if ("QUSER".equalsIgnoreCase(System.getProperty("user.name"))) {
            logDir.mkdirs();
            final String[] chmodCmd = new String[] { "/QOpenSys/usr/bin/chmod", "600", logDir.getAbsolutePath() };
            final Process chmodProcess = Runtime.getRuntime().exec(chmodCmd, null, new File("/tmp"));
            if (0 == chmodProcess.waitFor()) {
                ret = File.createTempFile(filePrefix, ".html", logDir);
            } else {
                ret = File.createTempFile(filePrefix, ".html");
            }
        } else {
            ret = File.createTempFile(filePrefix, ".html");
        }
        return m_destFile = ret;
    }
}
