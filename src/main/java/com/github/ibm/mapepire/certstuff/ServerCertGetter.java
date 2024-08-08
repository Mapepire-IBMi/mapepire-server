package com.github.ibm.mapepire.certstuff;

import java.io.File;
import java.io.IOException;

import com.github.ibm.mapepire.Tracer;

public class ServerCertGetter {

    private static class StoreDefaults {
        private static String STORE_PASS = "hyrule";
        private static String KEY_PASS = "hyrule";
        public static String ALIAS = "wsdb";

        public static String getStorePass() {
            String envVar = System.getenv("WSDB_STORE_PASS");
            if (envVar != null)
                return envVar;
            else
                return StoreDefaults.STORE_PASS;
        }

        public static String getKeyPass() {
            String envVar = System.getenv("WSDB_KEY_PASS");
            if (envVar != null)
                return envVar;
            else
                return StoreDefaults.KEY_PASS;
        }
    }

    private final File m_userCertFile; 
    private final File m_defaultCertFile;

    public ServerCertGetter() {
        final File userHomeDir = new File(System.getProperty("user.home"));
        final File dotDir = new File(userHomeDir, ".wsdb");
        dotDir.mkdirs();
        m_userCertFile = new File(dotDir, "user.jks");
        m_defaultCertFile = new File(dotDir, "default.jks");
    }

    public ServerCertInfo get() throws IOException, InterruptedException {
        if(m_userCertFile.isFile()) {
            Tracer.info("Using user-defined certificate file "+m_userCertFile.getAbsolutePath());
            return new ServerCertInfo(m_userCertFile, StoreDefaults.getStorePass(), StoreDefaults.getKeyPass(), StoreDefaults.ALIAS);
        } else if(m_defaultCertFile.isFile()) {
            Tracer.info("Reusing previously-generated default certificate in "+m_defaultCertFile.getAbsolutePath());
            return new ServerCertInfo(m_defaultCertFile, StoreDefaults.getStorePass(), StoreDefaults.getKeyPass(), StoreDefaults.ALIAS);
        } else  {
            Tracer.warn("Generating self-signed certificate");
            final SelfSignedCertGenerator gen = new SelfSignedCertGenerator();
            return gen.generate(StoreDefaults.getStorePass(), StoreDefaults.getKeyPass(), m_defaultCertFile, StoreDefaults.ALIAS);
        }
    }
}
