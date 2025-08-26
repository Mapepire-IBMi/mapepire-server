package com.github.ibm.mapepire;

import java.io.FileInputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.LinkedList;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.github.ibm.mapepire.ws.DbWebsocketClient;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.server.NativeWebSocketServletContainerInitializer;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeFilter;

import com.github.ibm.mapepire.Tracer.Dest;
import com.github.ibm.mapepire.Tracer.TraceLevel;
import com.github.ibm.mapepire.authfile.AuthFile;
import com.github.ibm.mapepire.certstuff.ServerCertGetter;
import com.github.ibm.mapepire.certstuff.ServerCertInfo;
import com.github.ibm.mapepire.ws.DbSocketCreator;
import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.StringUtils;

public class MapepireServer {
    private static Server server;
    // Required minimum Java version
    private static String minimumRequiredJavaVersion = "1.8.0_341";
    private volatile static boolean s_isSingleMode = false;

    public static boolean isSingleMode() { 
        return s_isSingleMode;
    }

    private static int getNumBytesInMb(int num){
        return num * 1024 * 1024;
    }

    public static void main(final String[] _args) {

        final LinkedList<String> args = new LinkedList<String>();
        args.addAll(Arrays.asList(_args));

        if (args.remove("--version")) {
            System.out.println("Version: " + Version.s_version);
            System.out.println("Build time: " + Version.s_compileDateTime);
            System.exit(0);
        }

        try {

            if (args.remove("--single")) {
                s_isSingleMode = true;
                // Make sure we can process our security rules file, if it exists
                AuthFile.getDefault().getRules();

                final SystemConnection conn = new SystemConnection();
                String testFile = System.getProperty("test.file", "");
                boolean testMode = StringUtils.isNonEmpty(testFile);
                if (testMode) {
                    System.setIn(new FileInputStream(testFile));
                }
//                final DataStreamProcessor io = new DataStreamProcessor(System.in, System.out, conn, testMode);
//                io.run();
            } else {
                s_isSingleMode = false;

                // Needed to enforce TLS capabilities when not in single mode
                checkJavaVersion(minimumRequiredJavaVersion);
                
                // Make sure we can process our security rules file, if it exists
                AuthFile.getDefault().getRules();

                Tracer.get().setDest(Dest.FILE);
                if(args.remove("--traceErrors")) {
                    Tracer.get().setTraceLevel(TraceLevel.ERRORS);
                }
                if(args.remove("--traceOn")) {
                    Tracer.get().setTraceLevel(TraceLevel.ON);
                }
                if(args.remove("--traceDs")) {
                    Tracer.get().setTraceLevel(TraceLevel.DATASTREAM);
                }
                AppLogger logger = AppLogger.getSingleton(args.remove("-v"));
                logger.printf("Starting daemon...");
                Tracer.info("Starting daemon...");
                DbSocketCreator.enableDaemon();
                
                server = new Server();

                final ServerConnector connector;
                String isUnsecure = System.getenv("MP_UNSECURE");
                if (StringUtils.isNonEmpty(isUnsecure) && isUnsecure.equals("true")) {
                    String uhOhWarning = "WARNING: Running in unsecure mode. Credentials are NOT encrypted!";
                    logger.println_err("\n\n" + uhOhWarning + "\n\n");
                    Tracer.warn(uhOhWarning);
                    connector = new ServerConnector(server);
                } else {
                    SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
                    ServerCertInfo serverCertInfo = new ServerCertGetter().get();
                    sslContextFactory.setKeyStore(serverCertInfo.getKeyStore());
                    sslContextFactory.setKeyStorePassword(serverCertInfo.getStorePass());
                    sslContextFactory.setCertAlias(serverCertInfo.getAlias());
                    sslContextFactory.setKeyManagerPassword(serverCertInfo.getKeyPass());
                    Tracer.info("Using key store " + serverCertInfo.getKeyStoreFile().getAbsolutePath());
                    connector = new ServerConnector(server, sslContextFactory);
                }

                server.addConnector(connector);

                // Setup the basic application "context" for this application at "/"
                // This is also known as the handler tree (in jetty speak)
                ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
                context.setContextPath("/");

                Constraint constraint = new Constraint();
                constraint.setName("Disable TRACE");
                constraint.setAuthenticate(true);

                ConstraintMapping disableTrace = new ConstraintMapping();
                disableTrace.setConstraint(constraint);
                disableTrace.setMethod("TRACE");
                disableTrace.setPathSpec("/");

                ConstraintMapping allowOthers = new ConstraintMapping();
                allowOthers.setConstraint(new Constraint());
                allowOthers.setMethod("*");
                allowOthers.setPathSpec("/");

                ConstraintSecurityHandler handler = new ConstraintSecurityHandler();
                handler.addConstraintMapping(disableTrace);
                handler.addConstraintMapping(allowOthers);

                handler.setHandler(context);
                server.setHandler(handler);

                String remoteServer = System.getenv("DB_SERVER"); //TODO: replace `System.getenv` calls with IBMiDotEnv or something. 
                if (StringUtils.isNonEmpty(remoteServer)) {
                    DbSocketCreator.setDatabaseHost(remoteServer);
                }
                if (SystemConnection.isRunningOnIBMi() && (StringUtils.isEmpty(remoteServer) || "localhost".equalsIgnoreCase(remoteServer) || "127.0.0.1".equalsIgnoreCase(remoteServer))) {
                    SSLContext ctx = SSLContext.getInstance("TLS");
                    //@formatter:off
                    ctx.init(null, new TrustManager[] { 
                            new X509TrustManager() { 
                                @Override  public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException { }
                                @Override  public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException { }
                                @Override  public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                            } 
                        }, null);
                    //@formatter:on
                    SSLContext.setDefault(ctx);
                }

                String remotePort = System.getenv("PORT");
                if (StringUtils.isNonEmpty(remotePort)) {
                    DbSocketCreator.setServerPort(Integer.parseInt(remotePort));
                }

                connector.setPort(DbSocketCreator.getPort());

                logger.println("Starting server for " + DbSocketCreator.getHost() + " on port " + DbSocketCreator.getPort());

                // Configure specific websocket behavior
                NativeWebSocketServletContainerInitializer.configure(context,
                        (servletContext, nativeWebSocketConfiguration) -> {
                            nativeWebSocketConfiguration.getPolicy().setMaxTextMessageBufferSize(65535);
                            // Configure max message size
                            int maxWsMessageSize = getNumBytesInMb(200);
                            int maxBinaryMessageSize = getNumBytesInMb(200);
                            String maxWsMessageSizeStr = System.getenv("MAX_WS_MESSAGE_SIZE");
                            if (StringUtils.isNonEmpty(maxWsMessageSizeStr)) {
                                maxWsMessageSize = Integer.parseInt(maxWsMessageSizeStr);
                            }
                            nativeWebSocketConfiguration.getPolicy().setMaxTextMessageSize(maxWsMessageSize);
                            nativeWebSocketConfiguration.getPolicy().setMaxBinaryMessageSize(maxBinaryMessageSize);


                            // Add websockets
                            nativeWebSocketConfiguration.addMapping("/db/*", new DbSocketCreator());
                        });

                // Add generic filter that will accept WebSocket upgrade.
                WebSocketUpgradeFilter.configure(context);

                try {
                    server.start();
                    server.join();
                    logger.println_warn("Server ending gracefully");
                } catch (Throwable t) {
                    logger.exception(t);
                }
            }
        } catch (final Exception e) {
            Tracer.err(e);
        }
        Tracer.warn("data stream processing completed (end of request stream?)");
        System.err.println("bye");
        System.exit(12);
    }

    /**
     * Check that the Java version is at least a certain level
     * @param requiredVersion The minimum required Java version
     * @implNote This method terminates the application with {@code System.exit(1)} if the
     * current Java version does not meet the required version.
     */
    private static void checkJavaVersion(String requiredVersion){

        // Get the current Java version
        String javaVersion = System.getProperty("java.version");

        // Compare versions
        if (isVersionLessThan(javaVersion, requiredVersion)) {
            System.err.println("Error: Java version must be >= " + requiredVersion
                    + ". Current version: " + javaVersion);
            System.exit(1); // Exit with an error code
        }
    }

    /**
     * Compares two Java versions and determines if the first is less than the second.
     *
     * @param currentVersion the current Java version.
     * @param requiredVersion the required Java version.
     * @return true if currentVersion < requiredVersion, false otherwise.
     */
    private static boolean isVersionLessThan(String currentVersion, String requiredVersion) {
        String[] currentParts = currentVersion.split("\\.|_|-");
        String[] requiredParts = requiredVersion.split("\\.|_|-");

        int length = Math.max(currentParts.length, requiredParts.length);
        for (int i = 0; i < length; i++) {
            String currentPart = (i < currentParts.length) ? currentParts[i] : "0";
            String requiredPart = (i < requiredParts.length) ? requiredParts[i] : "0";
            try {
                int currentNumericPart = Integer.parseInt(currentParts[i]);
                int requiredNumericPart = Integer.parseInt(requiredParts[i]);

                if (currentNumericPart < requiredNumericPart) {
                    return true;
                } else if (currentNumericPart > requiredNumericPart) {
                    return false;
                }
            }  catch (NumberFormatException e) {
                // If it's not a number, compare as strings
                int comparison = currentPart.compareTo(requiredPart);
                if (comparison < 0) {
                    return true;
                } else if (comparison > 0) {
                    return false;
                }
            }
        }
        return false;
    }
}
