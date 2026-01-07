package com.system.SchoolManagementSystem.config;

import com.system.SchoolManagementSystem.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException)
            throws IOException, ServletException {

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        String errorMessage = "Unauthorized access. Please provide valid credentials.";

        // Customize message based on request
        String requestPath = request.getRequestURI();
        if (requestPath.contains("/api/admin")) {
            errorMessage = "Admin access required. Please login with admin credentials.";
        }

        ApiResponse<Object> errorResponse = ApiResponse.error(errorMessage, "AUTH_REQUIRED");

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}