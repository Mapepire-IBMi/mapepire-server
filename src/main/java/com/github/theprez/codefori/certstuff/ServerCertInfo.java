package com.github.theprez.codefori.certstuff;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class ServerCertInfo {

    private final String m_storePass;
    private final File m_storePath;
    private KeyStore m_keystore = null;
    private final String m_keyPass;
    private final String m_alias;

    public ServerCertInfo(File _keyStore, String _storePassword, String _keyPassword, String _alias) {
        m_storePath = _keyStore;
        m_storePass = _storePassword;
        m_keyPass = _keyPassword;
        m_alias = _alias;
    }

    public KeyStore getKeyStore() throws KeyStoreException, NoSuchAlgorithmException,
            FileNotFoundException, IOException, CertificateException {
        if (null != m_keystore) {
            return m_keystore;
        }
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(m_storePath), m_storePass.toCharArray());
        return m_keystore = ks;
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
        return m_storePath;
    }
}