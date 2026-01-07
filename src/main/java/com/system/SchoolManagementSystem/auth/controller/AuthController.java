package com.system.SchoolManagementSystem.auth.controller;

import com.system.SchoolManagementSystem.auth.dto.*;
import com.system.SchoolManagementSystem.auth.entity.User;
import com.system.SchoolManagementSystem.auth.service.AuthService;
import com.system.SchoolManagementSystem.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        try {
            log.info("Login request received for user: {}", request.getUsername());
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
        } catch (RuntimeException e) {
            log.error("Login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            log.info("Registration request received for user: {}", request.getUsername());
            authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Registration successful. You can now login."));
        } catch (RuntimeException e) {
            log.error("Registration failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @RequestHeader("Authorization") String refreshToken) {
        try {
            log.info("Refresh token request received");
            LoginResponse response = authService.refreshToken(refreshToken);
            return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
        } catch (RuntimeException e) {
            log.error("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfile>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            log.info("Get current user request for: {}", userDetails.getUsername());
            UserProfile profile = authService.getCurrentUserProfile();
            return ResponseEntity.ok(ApiResponse.success(profile, "User profile retrieved successfully"));
        } catch (RuntimeException e) {
            log.error("Get current user failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        try {
            log.info("Change password request for user: {}", userDetails.getUsername());
            User user = (User) userDetails;
            String userId = user.getId();
            authService.changePassword(userId, request);
            return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
        } catch (RuntimeException e) {
            log.error("Change password failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfile>> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        try {
            log.info("Update profile request for user: {}", userDetails.getUsername());
            User user = (User) userDetails;
            String userId = user.getId();
            UserProfile updatedProfile = authService.updateProfile(userId, request);
            return ResponseEntity.ok(ApiResponse.success(updatedProfile, "Profile updated successfully"));
        } catch (RuntimeException e) {
            log.error("Update profile failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            log.info("Logout request for user: {}", userDetails.getUsername());
            User user = (User) userDetails;
            String userId = user.getId();
            authService.logout(userId);
            return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
        } catch (RuntimeException e) {
            log.error("Logout failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Admin endpoints
    @PutMapping("/users/{userId}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> enableUser(@PathVariable String userId) {
        try {
            log.info("Enable user request for user ID: {}", userId);
            authService.enableUser(userId);
            return ResponseEntity.ok(ApiResponse.success("User enabled successfully"));
        } catch (RuntimeException e) {
            log.error("Enable user failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/users/{userId}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> disableUser(@PathVariable String userId) {
        try {
            log.info("Disable user request for user ID: {}", userId);
            authService.disableUser(userId);
            return ResponseEntity.ok(ApiResponse.success("User disabled successfully"));
        } catch (RuntimeException e) {
            log.error("Disable user failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/users/{userId}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> unlockUser(@PathVariable String userId) {
        try {
            log.info("Unlock user request for user ID: {}", userId);
            authService.unlockUser(userId);
            return ResponseEntity.ok(ApiResponse.success("User unlocked successfully"));
        } catch (RuntimeException e) {
            log.error("Unlock user failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Handle validation exceptions for this controller only
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("Validation failed: {}", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed"));
    }

    // Health check endpoint
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "auth-service"));
    }
}