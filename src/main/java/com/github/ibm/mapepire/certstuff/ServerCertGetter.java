package com.github.ibm.mapepire.certstuff;

import java.io.File;
import java.io.IOException;
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
        final File userHomeDir = new File(System.getProperty("user.home")); //TODO: handle user swaps
        final File dotDir = new File(userHomeDir, ".mapepire");
        dotDir.mkdirs();
        m_letsEncrypt = ServerCertLetsEncrypt.get(findLetsEncryptCertDir());
        File userCertFile = new File ("/QOpenSys/etc/mapepire/cert/server.jks");
        m_userCertFile =  userCertFile.isFile() ? userCertFile :  new File ("/QOpenSys/etc/mapepire/cert/server.p12");
        m_defaultCertFile = new File(dotDir, "default.jks");
    }

    /**
     * Locate a usable Let's Encrypt certificate directory under /etc/letsencrypt/live.
     * Returns null when the directory is missing, inaccessible, empty, or has no readable
     * children. The canRead/canExecute guards avoid an NPE from listFiles() when the
     * process cannot list the directory.
     *
     * @return a candidate certificate directory, or null if none are usable
     */
    private File findLetsEncryptCertDir() {
        final File liveDir = new File("/etc/letsencrypt/live");
        if (!liveDir.isDirectory() || !liveDir.canRead() || !liveDir.canExecute()) {
            return null;
        }
        final File[] listing = liveDir.listFiles();
        if (null == listing) {
            return null;
        }
        final LinkedList<File> candidates = new LinkedList<File>();
        for (final File ls : listing) {
            if (ls.isDirectory() && ls.canRead() && ls.canExecute()) {
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
            final String myHostName = LocalHostResolver.getFQDN();
            Tracer.globalInfo("------we think our hostname is " + myHostName);
            for (final File candidate : candidates) {
                if (candidate.getName().equalsIgnoreCase(myHostName)) {
                    return candidate;
                }
            }

        } catch (final IOException e) {
        }
        return candidates.removeLast();
    }

    public ServerCertInfo get() throws IOException, InterruptedException {
        if (m_userCertFile.isFile() && m_userCertFile.canRead()) {
            Tracer.globalInfo("Reusing user-defined server certificate in " + m_userCertFile.getAbsolutePath());
            return new ServerCertJKS(m_userCertFile, StoreDefaults.getStorePass(), StoreDefaults.getKeyPass(),
                    StoreDefaults.getAlias());
        } else if (null != m_letsEncrypt) {
            Tracer.globalInfo("Using LetsEncrypt certificate file " + m_letsEncrypt.getKeyStoreFile().getAbsolutePath());
            return m_letsEncrypt;
        } else if (m_defaultCertFile.isFile() && m_defaultCertFile.canRead()) {
            Tracer.globalInfo("Reusing previously-generated default certificate in " + m_defaultCertFile.getAbsolutePath());
            return new ServerCertJKS(m_defaultCertFile, StoreDefaults.getStorePass(), StoreDefaults.getKeyPass(),
                    StoreDefaults.getAlias());
        } else {
            Tracer.globalWarn("Generating self-signed certificate");
            final SelfSignedCertGenerator gen = new SelfSignedCertGenerator();
            return gen.generate(StoreDefaults.getStorePass(), StoreDefaults.getKeyPass(), m_defaultCertFile,
                    StoreDefaults.getAlias());
        }
    }
}
