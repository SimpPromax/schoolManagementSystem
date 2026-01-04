package com.system.SchoolManagementSystem.demo.controller;

import com.system.SchoolManagementSystem.common.response.ApiResponse;
import com.system.SchoolManagementSystem.config.TenantContext;
import com.system.SchoolManagementSystem.demo.dto.DemoRequest;
import com.system.SchoolManagementSystem.demo.dto.DemoResponse;
import com.system.SchoolManagementSystem.demo.service.DemoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@Slf4j
public class DemoController {

    private final DemoService demoService;

    // Public endpoints (no authentication required)
    @GetMapping("/public")
    public ApiResponse<String> publicEndpoint() {
        return ApiResponse.success("This is a public endpoint - no authentication required");
    }

    @GetMapping("/public/message")
    public ApiResponse<String> publicMessage() {
        return ApiResponse.success("Welcome to School Management System Demo API");
    }

    @GetMapping("/public/health")
    public ApiResponse<String> healthCheck() {
        return ApiResponse.success("System is healthy and running");
    }

    @GetMapping("/public/tenant-info")
    public ApiResponse<String> getTenantInfo() {
        String tenantId = TenantContext.getCurrentTenant();
        String database = TenantContext.getCurrentDatabase();
        return ApiResponse.success(
                String.format("Current Tenant: %s, Database: %s", tenantId, database)
        );
    }

    // Protected endpoints (authentication required)
    @GetMapping("/protected/message")
    public ApiResponse<String> protectedMessage() {
        String tenantId = TenantContext.getCurrentTenant();
        String database = TenantContext.getCurrentDatabase();
        String username = "User"; // In real app, get from SecurityContext
        return ApiResponse.success(
                String.format("Welcome %s! You are accessing tenant: %s, database: %s",
                        username, tenantId, database)
        );
    }

    @GetMapping("/protected/tenant-details")
    public ApiResponse<String> getTenantDetails() {
        String tenantId = TenantContext.getCurrentTenant();
        String database = TenantContext.getCurrentDatabase();

        String details = """
            Tenant Information:
            -------------------
            Tenant ID: %s
            Database: %s
            Current Time: %s
            System Status: Active
            """.formatted(tenantId, database, java.time.LocalDateTime.now());

        return ApiResponse.success(details);
    }

    // Role-based endpoints
    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> adminDashboard() {
        return ApiResponse.success("Welcome to Admin Dashboard - Full system access");
    }

    @GetMapping("/admin/system-info")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> getSystemInfo() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        String info = """
            System Information:
            -------------------
            Java Version: %s
            Available Processors: %d
            Total Memory: %d MB
            Used Memory: %d MB
            Free Memory: %d MB
            Max Memory: %d MB
            """.formatted(
                System.getProperty("java.version"),
                runtime.availableProcessors(),
                totalMemory,
                usedMemory,
                freeMemory,
                runtime.maxMemory() / (1024 * 1024)
        );

        return ApiResponse.success(info);
    }

    @GetMapping("/teacher/dashboard")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<String> teacherDashboard() {
        return ApiResponse.success("Welcome to Teacher Dashboard - Manage your classes and students");
    }

    @GetMapping("/student/dashboard")
    @PreAuthorize("hasRole('STUDENT') or hasRole('USER')")
    public ApiResponse<String> studentDashboard() {
        return ApiResponse.success("Welcome to Student Dashboard - View your grades and schedule");
    }

    // Demo CRUD operations
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<DemoResponse>> createDemo(
            @Valid @RequestBody DemoRequest request) {
        DemoResponse response = demoService.createDemo(request);
        return ResponseEntity.ok(ApiResponse.success("Demo created successfully", response));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<DemoResponse>>> getAllDemos() {
        List<DemoResponse> demos = demoService.getAllDemos();
        return ResponseEntity.ok(ApiResponse.success("Demos retrieved successfully", demos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DemoResponse>> getDemoById(@PathVariable Long id) {
        DemoResponse demo = demoService.getDemoById(id);
        return ResponseEntity.ok(ApiResponse.success("Demo retrieved successfully", demo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DemoResponse>> updateDemo(
            @PathVariable Long id,
            @Valid @RequestBody DemoRequest request) {
        DemoResponse demo = demoService.updateDemo(id, request);
        return ResponseEntity.ok(ApiResponse.success("Demo updated successfully", demo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteDemo(@PathVariable Long id) {
        demoService.deleteDemo(id);
        return ResponseEntity.ok(ApiResponse.success("Demo deleted successfully"));
    }

    // Multi-tenant demo endpoints
    @GetMapping("/tenant-test")
    public ApiResponse<String> testMultiTenancy() {
        String tenantId = TenantContext.getCurrentTenant();
        String database = TenantContext.getCurrentDatabase();

        try {
            // This would test database connectivity
            String result = demoService.testDatabaseConnection();
            return ApiResponse.success(
                    String.format("Multi-tenancy test successful! Tenant: %s, Database: %s, Result: %s",
                            tenantId, database, result)
            );
        } catch (Exception e) {
            return ApiResponse.error(
                    String.format("Multi-tenancy test failed for Tenant: %s, Database: %s. Error: %s",
                            tenantId, database, e.getMessage())
            );
        }
    }
}