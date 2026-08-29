package com.github.ibm.mapepire;

import java.io.IOException;
import java.io.PrintStream;

public class SystemNativeUtils {

    private static native int writeToJobLog0(final String _msg) throws IOException;

    private static native long getPid0();

    private static final boolean s_isNativeLoaded;
    private static final String DEFAULT_NATIVE_PATH = "/qsys.lib/jesseg.lib/mapnative.srvpgm";

    static {
        if (SystemConnection.isRunningOnIBMi()) {
            boolean isNativeLoaded = false;
            try {
                System.load(System.getProperty("mapepire.natives", DEFAULT_NATIVE_PATH));
                System.err.println("INFO: Job logging facilities loaded");
                isNativeLoaded = true;
            } catch (Throwable _t) {
                _t.printStackTrace();
                isNativeLoaded = false;
            }
            s_isNativeLoaded = isNativeLoaded;
        } else {
            s_isNativeLoaded = false;
        }
    }

    public static void writeToJobLog(final String _msg) {
        if (s_isNativeLoaded) {
            try {
                writeToJobLog0(_msg.replace("\r", "").trim());
            } catch (IOException e) {
                // the best we can do...
                e.printStackTrace();
            }
        } else {
            System.err.println("Unpublished job log message: " + _msg);
        }
    }

    public static void printfToJobLog(final String _fmt, Object... _repldata) {
        writeToJobLog(String.format(_fmt, _repldata));
    }

    public static long getPid() {
        return s_isNativeLoaded ? getPid0() : -1L;
    }

    public static boolean isNativeLoaded() {
        return s_isNativeLoaded;
    }
}
