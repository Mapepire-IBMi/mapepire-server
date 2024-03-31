package com.github.theprez.codefori;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.LinkedList;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.server.NativeWebSocketServletContainerInitializer;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeFilter;
import com.github.theprez.codefori.certstuff.ServerCertGetter;
import com.github.theprez.codefori.certstuff.ServerCertInfo;
import com.github.theprez.codefori.ws.DbSocketCreator;
import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.StringUtils;
import com.github.theprez.jcmdutils.StringUtils.TerminalColor;

public class CodeForiServer {
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
                DbSocketCreator.enableDaemon();
                
                server = new Server();

                final ServerConnector connector;
                if (Boolean.getBoolean("wsdb.unsecure")) {
                    String uhOhWarning = "WARNING: Running in unsecure mode. Credentials are NOT encrypted!";
                    System.err.println(StringUtils.colorizeForTerminal("\n\n" + uhOhWarning + "\n\n",
                            TerminalColor.BRIGHT_RED));
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

                connector.setPort(8085);
                server.addConnector(connector);

                // Setup the basic application "context" for this application at "/"
                // This is also known as the handler tree (in jetty speak)
                ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
                context.setContextPath("/");
                server.setHandler(context);

                String remoteServer = System.getenv("DB_SERVER");
                if (StringUtils.isNonEmpty(remoteServer)) {
                    DbSocketCreator.setHost(remoteServer);
                }

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
                } catch (Throwable t) {
                    t.printStackTrace(System.err);
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