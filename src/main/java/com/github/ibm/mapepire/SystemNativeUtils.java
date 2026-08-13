package com.github.ibm.mapepire;

public class SystemNativeUtils {

    private static native void writeToJobLog0(final String _msg);

    private static native long getPid0();

    private static final boolean s_isNativeLoaded;

    static {
        s_isNativeLoaded = false;
    }

    public static void writeToJobLog(final Tracer _tracer, final String _msg) {
        if (s_isNativeLoaded) {
            // TODO: handle messages too long for job log
            writeToJobLog0(_msg);
        } else {
            _tracer.logInfo("Job log message not logged: " + _msg);
        }
    }

    public static void printfToJobLog(final Tracer _tracer, final String _fmt, Object... _repldata) {
        writeToJobLog(_tracer, String.format(_fmt, _repldata));
    }

    public static long getPid() {
        return s_isNativeLoaded ? getPid0() : -1L;
    }

}
