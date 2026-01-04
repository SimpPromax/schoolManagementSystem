package com.system.SchoolManagementSystem.config;

import com.system.SchoolManagementSystem.auth.service.CustomUserDetailsService;
import com.system.SchoolManagementSystem.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
@Order(2)
public class JwtRequestFilter extends OncePerRequestFilter {

    private final CustomUserDetailsService userDetailsService;
    private final JwtTokenUtil jwtTokenUtil;
    private final ObjectMapper objectMapper;

    // List of public endpoints that should bypass JWT filter
    private static final List<String> PUBLIC_ENDPOINTS = Arrays.asList(
            "/api/auth/login",
            "/api/auth/register",
            "/api/tenants/register",
            "/api/demo/public/",
            "/api/tenant-demo/",
            "/actuator/",
            "/swagger-ui/",
            "/v3/api-docs/",
            "/api-docs/"
    );

    public JwtRequestFilter(CustomUserDetailsService userDetailsService,
                            JwtTokenUtil jwtTokenUtil,
                            ObjectMapper objectMapper) {
        this.userDetailsService = userDetailsService;
        this.jwtTokenUtil = jwtTokenUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        final String requestTokenHeader = request.getHeader("Authorization");

        String username = null;
        String jwtToken = null;
        String tenantId = null;
        String databaseName = null;

        // Only process JWT if Authorization header is present
        if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
            jwtToken = requestTokenHeader.substring(7);
            try {
                username = jwtTokenUtil.getUsernameFromToken(jwtToken);
                tenantId = jwtTokenUtil.getTenantIdFromToken(jwtToken);
                databaseName = jwtTokenUtil.getDatabaseNameFromToken(jwtToken);

                // Set tenant context from JWT token
                if (tenantId != null && !tenantId.trim().isEmpty()) {
                    TenantContext.setCurrentTenant(tenantId.trim().toLowerCase());

                    if (databaseName != null && !databaseName.trim().isEmpty()) {
                        TenantContext.setCurrentDatabase(databaseName.trim().toLowerCase());
                    } else {
                        TenantContext.setCurrentDatabase(tenantId.trim().toLowerCase());
                    }

                    logger.debug("Set tenant context from JWT: tenantId=" + tenantId + ", databaseName=" + databaseName);
                }

            } catch (ExpiredJwtException e) {
                sendErrorResponse(response, "Token expired", HttpServletResponse.SC_UNAUTHORIZED);
                return;
            } catch (UnsupportedJwtException | MalformedJwtException | SignatureException | IllegalArgumentException e) {
                sendErrorResponse(response, "Invalid token", HttpServletResponse.SC_UNAUTHORIZED);
                return;
            } catch (JwtException e) {
                sendErrorResponse(response, "JWT validation failed: " + e.getMessage(), HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        } else {
            // No JWT token, continue with tenant context set by TenantFilter
            logger.debug("No JWT token found in request");
        }

        // If we have a username from JWT and no authentication in context yet
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                if (jwtTokenUtil.validateToken(jwtToken, userDetails)) {
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authenticationToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);

                    logger.debug("Authenticated user: " + username);
                }
            } catch (UsernameNotFoundException e) {
                sendErrorResponse(response, "User not found", HttpServletResponse.SC_UNAUTHORIZED);
                return;
            } catch (Exception e) {
                logger.error("Error loading user details: " + e.getMessage());
                sendErrorResponse(response, "Authentication failed", HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, String message, int status) throws IOException {
        response.setContentType("application/json");
        response.setStatus(status);
        ApiResponse<Object> errorResponse = ApiResponse.error(message);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Skip JWT filter for public endpoints
        for (String publicEndpoint : PUBLIC_ENDPOINTS) {
            if (path.startsWith(publicEndpoint)) {
                logger.debug("Skipping JWT filter for public endpoint: " + path);
                return true;
            }
        }

        // Also skip for root and error pages
        if (path.equals("/") || path.equals("/error") || path.equals("/favicon.ico")) {
            return true;
        }

        return false;
    }
}