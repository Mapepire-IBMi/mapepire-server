package com.ibm.ibmi_util;

import java.io.IOException;

public class SystemNativeUtils {

    public enum JobLogEnabling {
        FOUR_ZERO_SECLVL(1), FOUR_ZERO_SECLVL_JOBEND(2);

        private final int m_val;

        JobLogEnabling(final int _i) {
            m_val = _i;
        }

        int getNativeValue() {
            return m_val;
        }
    }

    private static final boolean s_isNativeLoaded;

    private static final String DEFAULT_NATIVE_PATH = "/qsys.lib/qaie.lib/qaijvantv.srvpgm";

    static {
        if (System.getProperty("os.name", "").contains("400")) {
            boolean isNativeLoaded = false;
            try {
                System.load(System.getProperty("mapepire.natives", DEFAULT_NATIVE_PATH));
                System.err.println("INFO: Job logging facilities loaded");
                isNativeLoaded = true;
            } catch (final Throwable _t) {
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
                writeToJobLog0(_msg.replace("\r", "").trim() + "\n");
            } catch (final IOException e) {
                // the best we can do...
                e.printStackTrace();
            }
        } else {
            System.err.println("Unpublished job log message: " + _msg);
        }
    }

    public static void printfToJobLog(final String _fmt, final Object... _repldata) {
        writeToJobLog(String.format(_fmt, _repldata));
    }

    public static long getPid() {
        return s_isNativeLoaded ? getPid0() : -1L;
    }

    public static boolean isNativeLoaded() {
        return s_isNativeLoaded;
    }

    private static native int writeToJobLog0(final String _msg) throws IOException;

    private static native long getPid0();

    private static native int enableJobLogging0(int _type);

    private static native int swapUser0();

    private static native String getCurrentUserProfile0();

    private static native String getCurrentUserHome0();

    public static String getCurrentUserProfileOrNull() {
        if (!s_isNativeLoaded) {
            return null;
        }
        String ret = getCurrentUserProfile0();
        if (null != ret) {
            ret = ret.trim();
        }
        return ret;
    }

    public static String getCurrentUserHomeOrNull() {
        if (!s_isNativeLoaded) {
            return null;
        }
        String ret = getCurrentUserHome0();
        if (null != ret) {
            ret = ret.trim();
        }
        return ret;
    }

    public static void enableJobLogging(final JobLogEnabling _jle) throws IOException {
        if (!s_isNativeLoaded) {
            return;
        }
        final int rc = enableJobLogging0(_jle.getNativeValue());
        if (0 != rc) {
            throw new IOException("Unable to enable job logging. rc=" + rc);
        }
    }

    public static String swapUser() throws IOException {
        if (!s_isNativeLoaded) {
            return System.getProperty("user.name");
        }

        final int rc = swapUser0();
        if (0 != rc) {
            throw new IOException("Unable to swap job user. rc=" + rc);
        }
        String userProfile = getCurrentUserProfileOrNull();
        if (null != userProfile) {
            System.setProperty("user.name", userProfile);
        }
        String userHome = getCurrentUserHomeOrNull();
        if (null != userHome) {
            System.setProperty("user.home", userHome);
        }
        return System.getProperty("user.name");
    }
}
