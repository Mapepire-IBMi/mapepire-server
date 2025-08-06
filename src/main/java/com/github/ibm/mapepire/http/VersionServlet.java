package com.github.ibm.mapepire.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.github.ibm.mapepire.Version;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class VersionServlet extends BaseJsonServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, String> response = new HashMap<>();
        response.put("version", Version.s_version);
        
        writeJsonResponse(resp, response);
    }
}