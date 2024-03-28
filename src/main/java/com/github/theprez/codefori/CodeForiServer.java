package com.github.theprez.codefori;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.LinkedList;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.server.NativeWebSocketServletContainerInitializer;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeFilter;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import com.github.theprez.codefori.ws.DbSocketCreator;
import com.github.theprez.jcmdutils.StringUtils;

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
                server = new Server();
                ServerConnector connector = new ServerConnector(server);
                connector.setPort(8085);
                server.addConnector(connector);

                // Setup the basic application "context" for this application at "/"
                // This is also known as the handler tree (in jetty speak)
                ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
                context.setContextPath("/");
                server.setHandler(context);

                String remoteServer = System.getProperty("remoteServer", "");
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