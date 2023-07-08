package com.github.theprez.codefori;

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
import java.util.Date;
import java.util.concurrent.LinkedBlockingDeque;

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
        DATASTREAM // all including data stream
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
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream stringStream = new PrintStream(baos, false);
                ((Throwable) m_data).printStackTrace(stringStream);
                stringStream.close();
                return new String(baos.toByteArray());
            } else {
                return "" + m_data;
            }
        }
    }

    private static Tracer s_instance = new Tracer();
    private static String s_pseudoPid = ("" + Math.random()).replace(".", "").replace("0", "");

    private static DateFormat s_dateFormatter = null;

    public static Tracer get() {
        return s_instance;
    }

    public static void info(Object _data) {
        get().Trace(EventType.INFO, _data);
    }

    public static void warn(Object _data) {
        get().Trace(EventType.WARN, _data);
    }

    public static void err(Object _data) {
        get().Trace(EventType.ERR, _data);
    }

    public static void datastreamIn(Object _data) {
        get().Trace(EventType.DATASTREAM_IN, _data);
    }

    public static void datastreamOut(Object _data) {
        get().Trace(EventType.DATASTREAM_OUT, _data);
    }

    private static DateFormat getDateFormatter() {
        if (null != s_dateFormatter) {
            return s_dateFormatter;
        }
        return s_dateFormatter = new SimpleDateFormat("yyyy-MM-dd'.'kk.mm.ss");
    }
    private LinkedBlockingDeque<Entry> m_inMem = new LinkedBlockingDeque<>(100);

    private LinkedBlockingDeque<String> m_jtOpenInMem = new LinkedBlockingDeque<>(16*1024);

    private Dest m_dest = Dest.IN_MEM;
    private OutputStreamWriter m_fileWriter = null;

    private PrintWriter m_jtOpenFileWriter = null;
    private File m_destFile = null;

    private File m_jtOpenDestFile = null;
    private TraceLevel m_traceLevel = TraceLevel.OFF;
    private TraceLevel m_jtOpenTraceLevel = TraceLevel.OFF;

    private Dest m_jtopenDest = Dest.IN_MEM;

    public Tracer setTraceLevel(TraceLevel _l) {
        m_traceLevel = _l;
        return this;
    }

    public Tracer setJtOpenTraceLevel(TraceLevel _l) {
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
        if (m_jtopenDest == _dest) {
            return this;
        }
        if (Dest.FILE == m_dest && null != m_jtOpenFileWriter) {
            m_jtOpenFileWriter.flush();
            m_jtOpenFileWriter.close();
            m_jtOpenFileWriter = null;
        }
        if (Dest.FILE == _dest) {
            m_jtOpenFileWriter = new PrintWriter(getJtOpenFile(), "UTF-8");
            Trace.setPrintWriter(m_jtOpenFileWriter);
        } else if (Dest.IN_MEM == _dest) {
            Trace.setPrintWriter(new PrintWriter(new Writer() {
                @Override
                public void write(char[] _cbuf, int _off, int _len) throws IOException {
                    m_jtOpenInMem.add(new String(_cbuf, _off, _len));
                }

                @Override
                public void flush() throws IOException {
                }

                @Override
                public void close() throws IOException {
                }
            }));
        }

        m_jtopenDest = _dest;
        return this;
    }

    public String getDestString() {
        switch (m_dest) {
            case FILE:
                return "FILE: " + (null == m_destFile ? null : m_destFile.getAbsolutePath());
            case IN_MEM:
                return "IN_MEM";
            default:
                return "unknown";
        }
    }
    public String getJtOpenDestString() {
        switch (m_dest) {
            case FILE:
                return "FILE: " + (null == m_jtOpenDestFile ? null : m_jtOpenDestFile.getAbsolutePath());
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
        StringBuffer buf = new StringBuffer();
        if (Dest.IN_MEM == m_dest) {
            synchronized (m_inMem) {
                for (Entry l : m_inMem) {
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
        return buf;
    }

    public StringBuffer getJtOpenRawData() throws UnsupportedEncodingException, FileNotFoundException, IOException {
        StringBuffer buf = new StringBuffer();
        if (Dest.IN_MEM == m_jtopenDest) {
            synchronized (m_jtOpenInMem) {
                for (String l : m_jtOpenInMem) {
                    buf.append(l);
                }
            }
        } else {
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
        if (!_t.isLoggedAt(m_traceLevel)) {
            return this;
        }
        if ((_data instanceof Throwable) && Boolean.getBoolean("codeserver.verbose")) { // TODO: audit fallback cases
                                                                                        // with use of printStackTrace()
                                                                                        // throughout
            ((Throwable) _data).printStackTrace();
        }
        if (Dest.IN_MEM == m_dest) {
            m_inMem.add(new Entry(_t, _data));
            return this;
        }
        if (null == m_fileWriter) {
            try {
                m_fileWriter = new OutputStreamWriter(new FileOutputStream(getFile(), true), "UTF-8");
                m_fileWriter.write(
                        new Entry(EventType.INFO,
                                String.format("Tracing enabled to file '%s'", m_destFile.getAbsolutePath()))
                                .asHtml());
            } catch (Exception e) {
                e.printStackTrace();
                m_dest = Dest.IN_MEM;
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
            return m_destFile = new File(dir, fileName);
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
            return m_jtOpenDestFile = new File(dir, fileName);
        } catch (Exception e) {
            return m_jtOpenDestFile = File.createTempFile("VSCode-jtopen", ".txt");
        }
    }
}
