package com.github.theprez.codefori.certstuff;

import java.io.File;
import java.io.IOException;

import com.github.theprez.codefori.Tracer;

public class ServerCertGetter {

    private static class StoreDefaults {
        public static String STORE_PASS = "hyrule";
        public static String KEY_PASS = "hyrule";
        public static String ALIAS = "wsdb";
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
            //TODO: allow user to set passwords and such
            Tracer.info("Using user-defined certificate file "+m_userCertFile.getAbsolutePath());
            return new ServerCertInfo(m_userCertFile, StoreDefaults.STORE_PASS, StoreDefaults.KEY_PASS, StoreDefaults.ALIAS);
        } else if(m_defaultCertFile.isFile()) {
            Tracer.info("Reusing previously-generated default certificate in "+m_defaultCertFile.getAbsolutePath());
            return new ServerCertInfo(m_defaultCertFile, StoreDefaults.STORE_PASS, StoreDefaults.KEY_PASS, StoreDefaults.ALIAS);
        } else  {
            Tracer.warn("Generating self-signed certificate");
            final SelfSignedCertGenerator gen = new SelfSignedCertGenerator();
            return gen.generate(StoreDefaults.KEY_PASS, StoreDefaults.KEY_PASS, m_defaultCertFile, StoreDefaults.ALIAS);
        }
    }
}
