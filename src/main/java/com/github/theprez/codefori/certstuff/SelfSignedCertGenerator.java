package com.github.theprez.codefori.certstuff;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;

import com.github.theprez.codefori.Tracer;

public class SelfSignedCertGenerator {

    public SelfSignedCertGenerator() {
    }

    public ServerCertInfo generate(String _keyPassword, String _storePassword, File _keyStore, String _alias)
            throws IOException, InterruptedException {

        File javaHome = new File(System.getProperty("java.home"));
        File keytoolDir = new File(javaHome, "bin");
        File keytoolPath = new File(keytoolDir, getKeytoolBinaryName());
        String[] cmdArray = new String[] {
                keytoolPath.getAbsolutePath(),
                "-genkey",
                "-alias",
                _alias,
                "-keyalg",
                "RSA",
                "-keypass",
                _keyPassword,
                "-storepass",
                _storePassword,
                "-keystore",
                _keyStore.getAbsolutePath()
        };

        final Process p = Runtime.getRuntime().exec(cmdArray);

        Thread stdoutLogger = new StreamLogger(p.getInputStream(), false);
        Thread stderrLogger = new StreamLogger(p.getErrorStream(), true);
        stderrLogger.start();
        stdoutLogger.start();

        try (OutputStreamWriter stream = new OutputStreamWriter(p.getOutputStream(), "UTF-8")) {
            InetAddress localHost = InetAddress.getLocalHost();
            String fqdn = localHost.getHostName();
            String[] keytoolInputs = new String[] {
                    fqdn, "Web Socket Server", "Db2 for IBM i", "", "", "", "Y\nY\nY\nY\nY"
            };
            for (String keytoolInput : keytoolInputs) {
                stream.write(keytoolInput);
                stream.write("\n");
                stream.flush();
            }

            int exitCode = p.waitFor();
            if (0 == exitCode) {
                Tracer.info("Created keystore at " + _keyStore.getAbsolutePath());
            } else {
                Tracer.err("Failed to create keystore");
            }
        } finally {
            p.destroy();
        }

        stderrLogger.join();
        stdoutLogger.join();

        return new ServerCertInfo(_keyStore, _storePassword, _keyPassword, _alias);
    }

    private class StreamLogger extends Thread {
        public StreamLogger(final InputStream _stream, final boolean _isError) {
            super(new Runnable() {
                @Override
                public void run() {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(_stream, "UTF-8"))) {
                        String line = null;
                        while (null != (line = reader.readLine())) {
                            if (_isError) {
                                Tracer.warn(line);
                            } else {
                                Tracer.info(line);
                            }
                        }
                    } catch (Exception e) {
                        Tracer.err(e);
                    }
                }
            }, "Stream logger, errorstream=" + _isError);
        }
    }

    private String getKeytoolBinaryName() {
        String osName = ManagementFactory.getOperatingSystemMXBean().getName();
        if (osName.toLowerCase().contains("windows")) {
            return "keytool.exe";
        }
        return "keytool";
    }
}
