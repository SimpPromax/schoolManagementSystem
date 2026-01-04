package com.system.SchoolManagementSystem.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TenantRequest {

    @NotBlank(message = "School name is required")
    @Size(min = 2, max = 255, message = "School name must be between 2 and 255 characters")
    private String name;

    @NotBlank(message = "Database name is required")
    @Pattern(regexp = "^[a-z0-9_]+$",
            message = "Database name can only contain lowercase letters, numbers, and underscores")
    @Size(min = 3, max = 50, message = "Database name must be between 3 and 50 characters")
    private String databaseName;

    @Email(message = "Email should be valid")
    @Size(max = 255, message = "Email must be less than 255 characters")
    private String email;

    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone number should be valid")
    private String phone;

    @Size(max = 1000, message = "Address must be less than 1000 characters")
    private String address;

    @Pattern(regexp = "^[a-z0-9]([a-z0-9\\-]*[a-z0-9])?$",
            message = "Domain can only contain lowercase letters, numbers, and hyphens")
    @Size(max = 255, message = "Domain must be less than 255 characters")
    private String domain;
}