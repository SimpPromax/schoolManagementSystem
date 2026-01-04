package com.system.SchoolManagementSystem.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {
    private String id;
    private String name;
    private String databaseName;
    private String domain;
    private String email;
    private String phone;
    private String address;
    private String status;
    private String adminUsername;
    private String adminPassword; // Only shown during creation
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}