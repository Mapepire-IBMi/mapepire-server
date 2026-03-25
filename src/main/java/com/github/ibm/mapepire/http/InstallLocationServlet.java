package com.github.ibm.mapepire.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
            URL location = InstallLocationServlet.class.getProtectionDomain().getCodeSource().getLocation();
            File f = new File(location.toURI());
            String installLocation = f.isDirectory() ? f.getAbsolutePath() : f.getParentFile().getAbsolutePath();
            response.put("install_location", installLocation);
            response.put("jar_path", f.getAbsolutePath());
        } catch (Exception e) {
            response.put("error", "Unable to determine install location: " + e.getMessage());
        }
        
        writeJsonResponse(resp, response);
    }
}
