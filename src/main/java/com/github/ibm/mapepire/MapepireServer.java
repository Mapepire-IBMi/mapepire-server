package com.github.ibm.mapepire;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.LinkedList;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.server.NativeWebSocketServletContainerInitializer;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeFilter;
import com.github.ibm.mapepire.certstuff.ServerCertGetter;
import com.github.ibm.mapepire.certstuff.ServerCertInfo;
import com.github.ibm.mapepire.ws.DbSocketCreator;
import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.StringUtils;
import com.github.theprez.jcmdutils.StringUtils.TerminalColor;

public class MapepireServer {
    private static Server server;

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
                final SystemConnection conn = new SystemConnection();
                String testFile = System.getProperty("test.file", "");
                boolean testMode = StringUtils.isNonEmpty(testFile);
                if (testMode) {
                    System.setIn(new FileInputStream(testFile));
                }
                final DataStreamProcessor io = new DataStreamProcessor(System.in, System.out, conn, testMode);

                io.run();
            } else {
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
                server.setHandler(context);

                String remoteServer = System.getenv("DB_SERVER"); //TODO: replace `System.getenv` calls with IBMiDotEnv or something. 
                if (StringUtils.isNonEmpty(remoteServer)) {
                    DbSocketCreator.setDatabaseHost(remoteServer);
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
                            // Configure default max size
                            nativeWebSocketConfiguration.getPolicy().setMaxTextMessageBufferSize(65535);

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
}
