package com.system.SchoolManagementSystem.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^\\+?[0-9\\s\\-()]{7,15}$", message = "Invalid phone number")
    private String phone;

    @Size(max = 500, message = "Address is too long")
    private String address;

    @Size(max = 255, message = "Profile picture URL is too long")
    private String profilePicture;
}