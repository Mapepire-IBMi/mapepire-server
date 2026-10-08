package com.github.ibm.mapepire.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.github.ibm.mapepire.MapepireServer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class InstallLocationServlet extends BaseJsonServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, String> response = new HashMap<>();
        
        // Get install location using the same approach as Tracer.java
        try {
            response.put("install_location", MapepireServer.getInstallLocationHumanReadable());
            response.put("jar_path", f.getAbsolutePath());
        } catch (Exception e) {
            response.put("error", "Unable to determine install location: " + e.getMessage());
        }
        
        writeJsonResponse(resp, response);
    }
}
