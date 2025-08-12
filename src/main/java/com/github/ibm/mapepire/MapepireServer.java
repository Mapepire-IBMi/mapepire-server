package com.github.ibm.mapepire;

import java.util.Arrays;
import java.util.LinkedList;

import com.github.ibm.mapepire.ws.DbSocketCreator;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.websocket.server.NativeWebSocketServletContainerInitializer;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeFilter;

public class MapepireServer {
    private static Server server;
    private volatile static boolean s_isSingleMode = false;

    public static boolean isSingleMode() {
        return s_isSingleMode;
    }

    public static void main(final String[] _args) {

        final LinkedList<String> args = new LinkedList<String>();
        args.addAll(Arrays.asList(_args));

        try {
            DbSocketCreator.enableDaemon();

            server = new Server();

            final ServerConnector connector;
            connector = new ServerConnector(server);


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

            connector.setPort(DbSocketCreator.getPort());


            // Configure specific websocket behavior
            NativeWebSocketServletContainerInitializer.configure(context,
                    (servletContext, nativeWebSocketConfiguration) -> {
                        nativeWebSocketConfiguration.getPolicy().setMaxTextMessageBufferSize(65535);
                        // Configure max message size
                        int maxWsMessageSize = 200 * 1024 * 1024; // 50MB
                        nativeWebSocketConfiguration.getPolicy().setMaxTextMessageSize(maxWsMessageSize);
                        // Add websockets
                        nativeWebSocketConfiguration.addMapping("/db/*", new DbSocketCreator());
                    });

            // Add generic filter that will accept WebSocket upgrade.
            WebSocketUpgradeFilter.configure(context);

            try {
                server.start();
                server.join();
            } catch (Throwable t) {
            }

        } catch (final Exception e) {
        }
        System.err.println("bye");
        System.exit(12);
    }

}
