package com.system.SchoolManagementSystem.auth.controller;

import com.system.SchoolManagementSystem.auth.dto.LoginRequest;
import com.system.SchoolManagementSystem.auth.dto.LoginResponse;
import com.system.SchoolManagementSystem.auth.dto.RegistrationRequest;
import com.system.SchoolManagementSystem.auth.entity.User;
import com.system.SchoolManagementSystem.auth.repository.UserRepository;
import com.system.SchoolManagementSystem.auth.service.CustomUserDetailsService;
import com.system.SchoolManagementSystem.common.response.ApiResponse;
import com.system.SchoolManagementSystem.config.JwtTokenUtil;
import com.system.SchoolManagementSystem.config.TenantContext;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final CustomUserDetailsService userDetailsService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager,
                          JwtTokenUtil jwtTokenUtil,
                          CustomUserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> registerUser(@Valid @RequestBody RegistrationRequest request) {
        String tenantId = TenantContext.getCurrentTenant();
        String databaseName = TenantContext.getCurrentDatabase();

        log.info("Registering user in tenant: {}, database: {}", tenantId, databaseName);

        if (userRepository.existsByUsernameAndTenantId(request.getUsername(), tenantId)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Username already exists in this tenant"));
        }

        User newUser = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .tenantId(tenantId)
                .databaseName(databaseName)
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .build();

        userRepository.save(newUser);
        return ResponseEntity.ok(ApiResponse.success("User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());

            String role = userDetails.getAuthorities().stream()
                    .findFirst()
                    .map(GrantedAuthority::getAuthority)
                    .map(auth -> auth.replace("ROLE_", ""))
                    .orElse("USER");

            String tenantId = TenantContext.getCurrentTenant();
            String databaseName = TenantContext.getCurrentDatabase();

            if (userDetails instanceof User) {
                User user = (User) userDetails;
                if (user.getDatabaseName() != null) {
                    databaseName = user.getDatabaseName();
                }
            }

            String token = jwtTokenUtil.generateToken(userDetails, tenantId, databaseName, role);

            LoginResponse loginResponse = new LoginResponse(
                    token, role, tenantId, databaseName, request.getUsername()
            );

            log.info("User {} logged in to tenant: {}, database: {}",
                    request.getUsername(), tenantId, databaseName);

            return ResponseEntity.ok(ApiResponse.success("Login successful", loginResponse));

        } catch (Exception e) {
            log.error("Login failed for user: {}", request.getUsername(), e);
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Invalid username or password"));
        }
    }

    @PostMapping("/register-admin")
    public ResponseEntity<ApiResponse<String>> registerAdmin(@Valid @RequestBody RegistrationRequest request) {
        if (!"master".equals(TenantContext.getCurrentTenant())) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("Only allowed from master tenant"));
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Username already exists"));
        }

        User adminUser = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("ADMIN")
                .tenantId("master")
                .databaseName("school_master")
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .build();

        userRepository.save(adminUser);
        return ResponseEntity.ok(ApiResponse.success("Admin user registered successfully"));
    }
}