package com.system.SchoolManagementSystem.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
@Order(1)
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // Extract tenant from header if JWT is not present
            String tenantIdentifier = extractTenantIdentifier(request);

            if (tenantIdentifier != null && !tenantIdentifier.trim().isEmpty()) {
                TenantContext.setCurrentTenant(tenantIdentifier.trim().toLowerCase());
                TenantContext.setCurrentDatabase(tenantIdentifier.trim().toLowerCase());
                log.debug("Set tenant context from header: {}", tenantIdentifier);
            } else {
                // For public endpoints without tenant header, use master database
                // But for login/register, we need to handle tenant differently
                String path = request.getRequestURI();

                if (path.contains("/api/auth/login") || path.contains("/api/auth/register")) {
                    // Try to get tenant from request parameter
                    String tenantParam = request.getParameter("tenantId");
                    if (tenantParam != null && !tenantParam.trim().isEmpty()) {
                        TenantContext.setCurrentTenant(tenantParam.trim().toLowerCase());
                        TenantContext.setCurrentDatabase(tenantParam.trim().toLowerCase());
                    } else {
                        // Default to master for auth endpoints
                        TenantContext.setCurrentTenant("master");
                        TenantContext.setCurrentDatabase("school_master");
                    }
                } else {
                    // Default to master for other public endpoints
                    TenantContext.setCurrentTenant("master");
                    TenantContext.setCurrentDatabase("school_master");
                }
                log.debug("Set default tenant context: master");
            }

            filterChain.doFilter(request, response);

        } finally {
            TenantContext.clear();
            log.debug("Cleared tenant context");
        }
    }

    private String extractTenantIdentifier(HttpServletRequest request) {
        // Try to get tenant from header first
        String tenantHeader = request.getHeader("X-Tenant-ID");
        if (tenantHeader != null && !tenantHeader.trim().isEmpty()) {
            return tenantHeader.trim().toLowerCase();
        }

        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Always filter - tenant context is needed for database routing
        return false;
    }
}