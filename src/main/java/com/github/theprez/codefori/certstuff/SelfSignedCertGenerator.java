package com.github.theprez.codefori.certstuff;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;

import com.github.theprez.codefori.Tracer;

public class SelfSignedCertGenerator {

    public SelfSignedCertGenerator() {
    }

    public ServerCertInfo generate(final String _keyPassword, final String _storePassword, final File _keyStore, final String _alias)
            throws IOException, InterruptedException {

        final File javaHome = new File(System.getProperty("java.home"));
        final File keytoolDir = new File(javaHome, "bin");

        final InetAddress localHost = InetAddress.getLocalHost();
        final String fqdn = localHost.getHostName();
        final String dname =  String.format("cn=%s, ou=Web Socket Server, o=Db2 for IBM i, c=Unknown, st=Unknown", fqdn);
        final File keytoolPath = new File(keytoolDir, getKeytoolBinaryName());
        final String[] cmdArray = new String[] {
                keytoolPath.getAbsolutePath(),
                "-genkey",
                "-dname",
                dname,
                "-alias",
                _alias,
                "-keyalg",
                "RSA",
                "-keypass",
                _keyPassword,
                "-storepass",
                _storePassword,
                "-keystore",
                _keyStore.getAbsolutePath(),
                "-validity",
                "3654"
        };

        final Process p = Runtime.getRuntime().exec(cmdArray);

        final Thread stdoutLogger = new StreamLogger(p.getInputStream(), false);
        final Thread stderrLogger = new StreamLogger(p.getErrorStream(), true);
        stderrLogger.start();
        stdoutLogger.start();

        p.getOutputStream().close();
        final int exitCode = p.waitFor();
        if (0 == exitCode) {
            Tracer.info("Created keystore at " + _keyStore.getAbsolutePath());
        } else {
            Tracer.err("Failed to create keystore");
        }

        stderrLogger.join();
        stdoutLogger.join();

        return new ServerCertInfo(_keyStore, _storePassword, _keyPassword, _alias);
    }

    private class StreamLogger extends Thread {
        public StreamLogger(final InputStream _stream, final boolean _isError) {
            super(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(_stream, "UTF-8"))) {
                        String line = null;
                        while (null != (line = reader.readLine())) {
                            if (_isError) {
                                Tracer.warn(line);
                            } else {
                                Tracer.info(line);
                            }
                        }
                    } catch (final Exception e) {
                        Tracer.err(e);
                    }
            }, "Stream logger, errorstream=" + _isError);
        }
    }

    private String getKeytoolBinaryName() {
        final String osName = ManagementFactory.getOperatingSystemMXBean().getName();
        if (osName.toLowerCase().contains("windows")) {
            return "keytool.exe";
        }
        return "keytool";
    }
}
