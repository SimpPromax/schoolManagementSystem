package com.system.SchoolManagementSystem.config;

import com.system.SchoolManagementSystem.auth.entity.User;
import com.system.SchoolManagementSystem.auth.repository.UserRepository;
import com.system.SchoolManagementSystem.tenant.entity.Tenant;
import com.system.SchoolManagementSystem.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner initDatabase() {
        return args -> {
            log.info("Initializing database...");

            // Create super admin if not exists
            if (!userRepository.existsByUsername("superadmin")) {
                User superAdmin = User.builder()
                        .username("superadmin")
                        .password(passwordEncoder.encode("admin123"))
                        .role("ADMIN")
                        .tenantId("master")
                        .databaseName("school_master")
                        .fullName("Super Administrator")
                        .email("admin@schoolsystem.com")
                        .phone("+1234567890")
                        .build();
                userRepository.save(superAdmin);
                log.info("Created super admin user");
            }

            // Create master tenant if not exists
            if (!tenantRepository.existsByDatabaseName("school_master")) {
                Tenant masterTenant = Tenant.builder()
                        .databaseName("school_master")
                        .name("Master System")
                        .status("ACTIVE")
                        .build();
                tenantRepository.save(masterTenant);
                log.info("Created master tenant");
            }

            // Create sample tenant for testing
            if (!tenantRepository.existsByDatabaseName("sample_school")) {
                Tenant sampleTenant = Tenant.builder()
                        .databaseName("sample_school")
                        .name("Sample High School")
                        .email("info@sample.edu")
                        .status("ACTIVE")
                        .adminUsername("sample_admin")
                        .adminPassword(passwordEncoder.encode("Sample@123"))
                        .build();
                tenantRepository.save(sampleTenant);

                // Create sample tenant admin user
                User sampleAdmin = User.builder()
                        .username("sample_admin")
                        .password(passwordEncoder.encode("Sample@123"))
                        .role("TENANT_ADMIN")
                        .tenantId("sample_school")
                        .databaseName("sample_school")
                        .fullName("Sample School Admin")
                        .email("admin@sample.edu")
                        .build();
                userRepository.save(sampleAdmin);

                log.info("Created sample tenant and admin user");
            }

            log.info("Database initialization completed successfully!");
        };
    }
}