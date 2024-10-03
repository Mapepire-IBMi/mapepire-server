package com.github.ibm.mapepire.certstuff;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;

import com.github.ibm.mapepire.Tracer;

class LocalHostResolver {
    static String getFQDN() throws IOException {
        try {
            return InetAddress.getLocalHost().getCanonicalHostName().toLowerCase();
        } catch (IOException e) {
            Tracer.warn(e);
            Process p = Runtime.getRuntime().exec("/QOpenSys/usr/bin/hostname");
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                return br.readLine().toLowerCase().trim();
            }
        }
    }
}
