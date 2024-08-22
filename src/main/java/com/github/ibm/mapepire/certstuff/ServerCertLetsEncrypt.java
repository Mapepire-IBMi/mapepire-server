package com.github.ibm.mapepire.certstuff;

import java.io.File;
import java.io.FileNotFoundException;

public class ServerCertLetsEncrypt extends ServerCertPEM {

    public ServerCertLetsEncrypt(File _dir) throws FileNotFoundException {
        super(new File(_dir, "fullchain.pem"), new File(_dir, "privkey.pem"));
    }
    public static ServerCertLetsEncrypt get(File _dir) {
        if(null == _dir) {
            return null;
        }
        try{
            ServerCertLetsEncrypt ret = new ServerCertLetsEncrypt(_dir);
            ret.getKeyStore();
            return ret;
        }catch(Exception _e) {
            _e.printStackTrace();
            return null;
        }
    }
}
