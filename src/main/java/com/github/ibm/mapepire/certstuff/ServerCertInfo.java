package com.github.ibm.mapepire.certstuff;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public interface ServerCertInfo {

    public KeyStore getKeyStore() throws KeyStoreException, NoSuchAlgorithmException, FileNotFoundException, IOException, CertificateException, InterruptedException;

    public String getAlias();

    public String getStorePass() ;

    public String getKeyPass();

    public File getKeyStoreFile();
}