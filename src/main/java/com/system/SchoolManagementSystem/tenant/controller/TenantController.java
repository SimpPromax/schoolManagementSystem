package com.system.SchoolManagementSystem.tenant.controller;

import com.system.SchoolManagementSystem.common.response.ApiResponse;
import com.system.SchoolManagementSystem.tenant.dto.TenantRequest;
import com.system.SchoolManagementSystem.tenant.dto.TenantResponse;
import com.system.SchoolManagementSystem.tenant.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
@Slf4j
public class TenantController {

    private final TenantService tenantService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TenantResponse>> registerTenant(
            @Valid @RequestBody TenantRequest request) {
        log.info("Registering new tenant: {}", request.getName());

        TenantResponse response = tenantService.createTenant(request);
        return new ResponseEntity<>(
                ApiResponse.success("Tenant registered successfully", response),
                HttpStatus.CREATED
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TenantResponse>>> getAllTenants() {
        List<TenantResponse> tenants = tenantService.getAllTenants();
        return ResponseEntity.ok(
                ApiResponse.success("Tenants retrieved successfully", tenants)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TenantResponse>> getTenantById(
            @PathVariable String id) {
        TenantResponse tenant = tenantService.getTenantById(id);
        return ResponseEntity.ok(
                ApiResponse.success("Tenant retrieved successfully", tenant)
        );
    }

    @GetMapping("/database/{databaseName}")
    public ResponseEntity<ApiResponse<TenantResponse>> getTenantByDatabaseName(
            @PathVariable String databaseName) {
        TenantResponse tenant = tenantService.getTenantByDatabaseName(databaseName);
        return ResponseEntity.ok(
                ApiResponse.success("Tenant retrieved successfully", tenant)
        );
    }
}