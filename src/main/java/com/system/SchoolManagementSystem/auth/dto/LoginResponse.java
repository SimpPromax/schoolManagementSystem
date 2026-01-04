package com.system.SchoolManagementSystem.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String tokenType = "Bearer";
    private String role;
    private String tenantId;
    private String databaseName;
    private String username;

    public LoginResponse(String token, String role, String tenantId, String databaseName, String username) {
        this.token = token;
        this.role = role;
        this.tenantId = tenantId;
        this.databaseName = databaseName;
        this.username = username;
    }
}