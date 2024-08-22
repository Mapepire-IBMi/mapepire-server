package com.github.ibm.mapepire.certstuff;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.UUID;

import com.github.ibm.mapepire.Tracer;

public class ServerCertPEM implements ServerCertInfo {

    private final String m_storePass;
    private KeyStore m_keystore = null;
    private final String m_keyPass;
    private final String m_alias;
    private final File m_file;
    private final File m_privateKeyFile;

    public ServerCertPEM(final File _pemFile, final File _privKeyFile) throws FileNotFoundException {
        if (!_pemFile.exists())
            throw new FileNotFoundException(_pemFile.getAbsolutePath());
        if (!_privKeyFile.exists())
            throw new FileNotFoundException(_privKeyFile.getAbsolutePath());
        m_file = _pemFile;
        m_privateKeyFile = _privKeyFile;
        m_storePass = UUID.randomUUID().toString().replace("-", "").substring(0, 9);
        System.out.println("password is " + m_storePass);
        m_keyPass = m_storePass;
        m_alias = "mapepire";

    }

    public KeyStore getKeyStore() throws KeyStoreException, NoSuchAlgorithmException,
            FileNotFoundException, IOException, CertificateException, InterruptedException {
        if (null != m_keystore) {
            return m_keystore;
        }

        File p12 = File.createTempFile("mppcert", ".p12");
        String[] cmd = new String[] {
                "/QOpenSys/usr/bin/openssl",
                "pkcs12",
                "-export",
                "-out",
                p12.getAbsolutePath(),
                "-inkey",
                m_privateKeyFile.getAbsolutePath(),
                "-in", m_file.getAbsolutePath(), "-name", m_alias, "-password", "pass:" + m_storePass
        };
        String[] env = new String[] { "QIBM_USE_DESCRIPTOR_STDIO=Y" }; // no idea why this is needed, but without it,
                                                                       // openssl fails with a -1 return code
        Process p = Runtime.getRuntime().exec(cmd, env);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line = null;
            while (null != (line = br.readLine())) {
                Tracer.warn("stderr from openssl call: " + line);
            }
        }
        p.waitFor();
        if (p.exitValue() != 0 || !p12.isFile()) {
            Tracer.warn("openssl failed with rc: " + p.exitValue());
            throw new IOException("intermediate export failed");
        }

        try (FileInputStream fis = new FileInputStream(p12)) {
            KeyStore ret = KeyStore.getInstance("PKCS12");
            ret.load(fis, m_storePass.toCharArray());
            Tracer.info("loaded temporary PKCS12 (generated from PEM)");
            return m_keystore = ret;
        }
    }

    public String getAlias() {
        return m_alias;
    }

    public String getStorePass() {
        return m_storePass;
    }

    public String getKeyPass() {
        return m_keyPass;
    }

    public File getKeyStoreFile() {
        return m_file;
    }
}