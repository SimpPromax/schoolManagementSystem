package com.system.SchoolManagementSystem.demo.controller;

import com.system.SchoolManagementSystem.common.response.ApiResponse;
import com.system.SchoolManagementSystem.config.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant-demo")
@Slf4j
public class TenantDemoController {

    // Test tenant switching via different methods
    @GetMapping("/test-headers")
    public ApiResponse<Map<String, String>> testTenantHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();

        String tenantHeader = request.getHeader("X-Tenant-ID");
        String authHeader = request.getHeader("Authorization");

        headers.put("X-Tenant-ID", tenantHeader != null ? tenantHeader : "Not provided");
        headers.put("Authorization", authHeader != null ? "Present" : "Not provided");
        headers.put("Current Tenant", TenantContext.getCurrentTenant());
        headers.put("Current Database", TenantContext.getCurrentDatabase());
        headers.put("Server Name", request.getServerName());

        log.info("Tenant headers test - Tenant: {}, Database: {}",
                TenantContext.getCurrentTenant(), TenantContext.getCurrentDatabase());

        return ApiResponse.success("Tenant headers test successful", headers);
    }

    @GetMapping("/switch-test")
    public ApiResponse<Map<String, Object>> testTenantSwitching() {
        Map<String, Object> response = new HashMap<>();

        String originalTenant = TenantContext.getCurrentTenant();
        String originalDatabase = TenantContext.getCurrentDatabase();

        response.put("originalTenant", originalTenant);
        response.put("originalDatabase", originalDatabase);
        response.put("timestamp", java.time.LocalDateTime.now());
        response.put("message", "This demonstrates tenant context switching");

        // Simulate tenant switching
        Map<String, String> simulatedTenants = new HashMap<>();
        simulatedTenants.put("master", "school_master");
        simulatedTenants.put("school_a", "school_a_db");
        simulatedTenants.put("school_b", "school_b_db");

        response.put("availableTenants", simulatedTenants);

        return ApiResponse.success("Tenant switching test", response);
    }

    @GetMapping("/jwt-info")
    public ApiResponse<Map<String, String>> getJwtInfo(HttpServletRequest request) {
        Map<String, String> jwtInfo = new HashMap<>();

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwtInfo.put("hasJWT", "true");
            jwtInfo.put("tokenPresent", "yes");
            // In a real app, you would decode the JWT here
            // String token = authHeader.substring(7);
            // Decode and extract claims
        } else {
            jwtInfo.put("hasJWT", "false");
            jwtInfo.put("tokenPresent", "no");
        }

        jwtInfo.put("currentTenant", TenantContext.getCurrentTenant());
        jwtInfo.put("currentDatabase", TenantContext.getCurrentDatabase());
        jwtInfo.put("authenticationRequired", "true for protected endpoints");

        return ApiResponse.success("JWT Information", jwtInfo);
    }

    @GetMapping("/database-operations")
    public ApiResponse<Map<String, Object>> testDatabaseOperations() {
        Map<String, Object> result = new HashMap<>();

        String tenantId = TenantContext.getCurrentTenant();
        String database = TenantContext.getCurrentDatabase();

        result.put("tenantId", tenantId);
        result.put("database", database);
        result.put("operation", "READ");
        result.put("table", "demo_entities");
        result.put("timestamp", java.time.LocalDateTime.now());

        // Simulate database operation results
        Map<String, String> sampleData = new HashMap<>();
        sampleData.put("id", "1");
        sampleData.put("name", "Test Data");
        sampleData.put("createdBy", "system");
        sampleData.put("status", "active");

        result.put("sampleData", sampleData);
        result.put("message", "Database operations are tenant-isolated");

        return ApiResponse.success("Database operations test", result);
    }
}