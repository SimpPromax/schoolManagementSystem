package com.system.SchoolManagementSystem.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfile {

    private String id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String address;
    private String profilePicture;
    private String role;
    private Boolean isAccountNonLocked;
    private Boolean isEnabled;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}