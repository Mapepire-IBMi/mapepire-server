package com.github.ibm.mapepire.http;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;

public abstract class BaseJsonServlet extends HttpServlet{
    protected final Gson gson = new Gson();

    protected void writeJsonResponse(HttpServletResponse resp, Object responseObj) throws java.io.IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setStatus(javax.servlet.http.HttpServletResponse.SC_OK);
        
        resp.getWriter().write(gson.toJson(responseObj));
    }

    protected void writeErrorResponse(HttpServletResponse resp, String errorMessage, int statusCode) throws java.io.IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setStatus(statusCode);
        
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", errorMessage);
        resp.getWriter().write(gson.toJson(errorResponse));
    }
    
}
