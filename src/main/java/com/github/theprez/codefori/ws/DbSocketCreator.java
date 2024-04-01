package com.github.theprez.codefori.ws;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;


public class DbSocketCreator implements WebSocketCreator
{
    private static boolean isDaemon = false;
    private static String host = "localhost";
    private static int port = 8076;

    public static void enableDaemon() {
        isDaemon = true;
    }

    public static boolean isDaemon() {
        return isDaemon;
    }

    public static void setDatabaseHost(String _host) {
        host = _host;
    }

    public static String getHost() {
        return host;
    }

    public static void setServerPort(int _port) {
        port = _port;
    }

    public static int getPort() {
        return port;
    }

    @Override
    public Object createWebSocket(ServletUpgradeRequest jettyServerUpgradeRequest, ServletUpgradeResponse jettyServerUpgradeResponse) {
        String auth = jettyServerUpgradeRequest.getHeader("authorization");

        if (auth == null) {
            try {
                jettyServerUpgradeResponse.sendForbidden("Authorization header missing");
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return null;
        }

        if (!auth.startsWith("Basic ")) {
            try {
                jettyServerUpgradeResponse.sendForbidden("Invalid Authorization header");
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return null;
        }
 
        // base64 decode
        String asBase64 = auth.substring(6).trim();
        byte[] decoded = Base64.getDecoder().decode(asBase64);
        String userPass = new String(decoded, StandardCharsets.UTF_8);

        String[] parts = userPass.split(":");

        if (parts.length != 2) {
            try {
                jettyServerUpgradeResponse.sendForbidden("Invalid Authorization header");
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return null;
        }
        
        try {
            return new DbWebsocketClient(DbSocketCreator.getHost(), parts[0], parts[1]);
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
    }
}