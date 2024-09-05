package com.github.ibm.mapepire.certstuff;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.LinkedList;
import com.github.ibm.mapepire.Tracer;
import com.github.theprez.jcmdutils.StringUtils;

public class ServerCertGetter {

    private static class StoreDefaults {
        private static String STORE_PASS = "mapepire";
        private static String KEY_PASS = "mapepire";
        public static String ALIAS = "mapepire";

        public static String getStorePass() {
            String envVar = System.getenv("MAPEPIRE_STORE_PASS");
            if (StringUtils.isNonEmpty(envVar))
                return envVar;
            else
                return StoreDefaults.STORE_PASS;
        }

        public static String getKeyPass() {
            String envVar = System.getenv("MAPEPIRE_KEY_PASS");
            if (StringUtils.isNonEmpty(envVar))
                return envVar;
            else
                return StoreDefaults.KEY_PASS;
        }

        public static String getAlias() {
            String envVar = System.getenv("MAPEPIRE_ALIAS");
            if (StringUtils.isNonEmpty(envVar))
                return envVar;
            else
                return StoreDefaults.ALIAS;
        }
    }

    private final ServerCertLetsEncrypt m_letsEncrypt;
    private final File m_defaultCertFile;
    private final File m_userCertFile;

    public ServerCertGetter() {
        final File userHomeDir = new File(System.getProperty("user.home"));
        final File dotDir = new File(userHomeDir, ".mapepire");
        dotDir.mkdirs();
        m_letsEncrypt = ServerCertLetsEncrypt.get(findLetsEncryptCertDir());
        File userCertFile = new File ("/QOpenSys/etc/mapepire/cert/server.jks");
        m_userCertFile =  userCertFile.isFile() ? userCertFile :  new File ("/QOpenSys/etc/mapepire/cert/server.p12");
        m_defaultCertFile = new File(dotDir, "default.jks");
    }

    private File findLetsEncryptCertDir() {
        File liveDir = new File("/etc/letsencrypt/live");
        if (!liveDir.isDirectory()) {
            return null;
        }
        LinkedList<File> candidates = new LinkedList<File>();
        for (File ls : liveDir.listFiles()) {
            if (ls.isDirectory()) {
                candidates.add(ls);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (1 == candidates.size()) {
            return candidates.removeFirst();
        }

        try {
            String myHostName = InetAddress.getLocalHost().getCanonicalHostName().toLowerCase();
            Tracer.info("------we think our hostname is "+myHostName);
            for (File candidate : candidates) {
                if (candidate.getName().equalsIgnoreCase(myHostName)) {
                    return candidate;
                }
            }

        } catch (IOException e) {
        }
        return candidates.removeLast();
    }

    public ServerCertInfo get() throws IOException, InterruptedException {
        if (m_userCertFile.isFile()) {
            Tracer.info("Reusing user-defined server certificate in " + m_defaultCertFile.getAbsolutePath());
            return new ServerCertJKS(m_defaultCertFile, StoreDefaults.getStorePass(), StoreDefaults.getKeyPass(),
                    StoreDefaults.getAlias());
        } else if (null != m_letsEncrypt) {
            Tracer.info("Using LetsEncrypt certificate file " + m_letsEncrypt.getKeyStoreFile().getAbsolutePath());
            return m_letsEncrypt;
        } else if (m_defaultCertFile.isFile()) {
            Tracer.info("Reusing previously-generated default certificate in " + m_defaultCertFile.getAbsolutePath());
            return new ServerCertJKS(m_defaultCertFile, StoreDefaults.getStorePass(), StoreDefaults.getKeyPass(),
                    StoreDefaults.getAlias());
        } else {
            Tracer.warn("Generating self-signed certificate");
            final SelfSignedCertGenerator gen = new SelfSignedCertGenerator();
            return gen.generate(StoreDefaults.getStorePass(), StoreDefaults.getKeyPass(), m_defaultCertFile,
                    StoreDefaults.getAlias());
        }
    }
}
