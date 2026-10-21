package com.github.ibm.mapepire.http;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.github.ibm.mapepire.MapepireServer;
import com.github.ibm.mapepire.Tracer;

/**
 * Reports where the running server was loaded from, as both the code source URL and the absolute path of
 * the jar file.
 */
public class InstallLocationServlet extends BaseJsonServlet {
    @Override
    protected void doGet(final HttpServletRequest _req, final HttpServletResponse _resp) throws IOException {
        final Map<String, String> response = new HashMap<>();

        try {
            final String installLocation = MapepireServer.getInstallLocationHumanReadable();
            response.put("install_location", installLocation);
            response.put("jar_path", new File(new URI(installLocation)).getAbsolutePath());
        } catch (final Exception _e) {
            // No per-connection tracer exists for HTTP requests, so log to the global tracer
            Tracer.globalErr(_e);
            response.put("error", "Unable to determine install location: " + _e.getMessage());
        }

        writeJsonResponse(_resp, response);
    }
}
