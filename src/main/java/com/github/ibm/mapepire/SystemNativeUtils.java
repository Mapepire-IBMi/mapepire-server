package com.github.ibm.mapepire;

import java.io.IOException;

public class SystemNativeUtils {

    private static native void writeToJobLog0(final String _msg) throws IOException;

    private static native long getPid0();

    private static final boolean s_isNativeLoaded;

    static {
        s_isNativeLoaded = false;
    }

    public static void writeToJobLog(final Tracer _tracer, final String _msg) {
        if (s_isNativeLoaded) {
            try {
                writeToJobLog0(_msg.replace("\r", "").trim());
            } catch (IOException e) {
                // the best we can do...
                e.printStackTrace();
            }
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

    public static boolean isNativeLoaded() {
        return s_isNativeLoaded;
    }

}
