package com.github.ibm.mapepire.ws;

import java.io.IOException;
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
        
        // Parse username and password from decoded bytes without creating String for password
        int colonIndex = -1;
        for (int i = 0; i < decoded.length; i++) {
            if (decoded[i] == ':') {
                colonIndex = i;
                break;
            }
        }

        if (colonIndex == -1 || colonIndex == 0 || colonIndex == decoded.length - 1) {
            try {
                jettyServerUpgradeResponse.sendForbidden("Invalid Authorization header");
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return null;
        }
        
        try {
            // Extract username as String
            String username = new String(decoded, 0, colonIndex, StandardCharsets.UTF_8);
            
            // Extract password as char[] directly without creating intermediate String
            int passwordLength = decoded.length - colonIndex - 1;
            char[] password = new char[passwordLength];
            for (int i = 0; i < passwordLength; i++) {
                password[i] = (char) decoded[colonIndex + 1 + i];
            }
            
            return new DbWebsocketClient(jettyServerUpgradeRequest.getRemoteHostName(), jettyServerUpgradeRequest.getRemoteAddress(), DbSocketCreator.getHost(), username, password, asBase64);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
    }
}