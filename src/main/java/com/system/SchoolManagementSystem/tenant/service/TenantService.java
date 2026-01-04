package com.system.SchoolManagementSystem.tenant.service;

import com.system.SchoolManagementSystem.auth.entity.User;
import com.system.SchoolManagementSystem.auth.repository.UserRepository;
import com.system.SchoolManagementSystem.config.DatabaseConfig;
import com.system.SchoolManagementSystem.config.TenantContext;
import com.system.SchoolManagementSystem.tenant.dto.TenantRequest;
import com.system.SchoolManagementSystem.tenant.dto.TenantResponse;
import com.system.SchoolManagementSystem.tenant.entity.Tenant;
import com.system.SchoolManagementSystem.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;
    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final DatabaseConfig databaseConfig;

    @Transactional
    public TenantResponse createTenant(TenantRequest request) {
        log.info("Creating new tenant: {}", request.getName());

        if (tenantRepository.existsByDatabaseName(request.getDatabaseName())) {
            throw new RuntimeException("Database name '" + request.getDatabaseName() + "' already exists");
        }

        String adminUsername = request.getDatabaseName() + "_admin";
        String adminPassword = UUID.randomUUID().toString().substring(0, 12);

        Tenant tenant = Tenant.builder()
                .name(request.getName())
                .databaseName(request.getDatabaseName().toLowerCase())
                .domain(request.getDomain() != null ? request.getDomain().toLowerCase() : null)
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .adminUsername(adminUsername)
                .adminPassword(passwordEncoder.encode(adminPassword))
                .status("ACTIVE")
                .build();

        Tenant savedTenant = tenantRepository.save(tenant);
        log.info("Saved tenant to master database: {}", savedTenant.getId());

        try {
            // Create tenant database using auto-create feature
            createTenantDatabase(savedTenant.getDatabaseName());

            // Initialize tenant database with required tables
            initializeTenantDatabase(savedTenant.getDatabaseName());

            // Create tenant admin user
            createTenantAdminUser(savedTenant, adminUsername, adminPassword);

            log.info("Successfully created tenant database: {}", savedTenant.getDatabaseName());

            TenantResponse response = mapToResponse(savedTenant);
            response.setAdminUsername(adminUsername);
            response.setAdminPassword(adminPassword);

            return response;

        } catch (Exception e) {
            log.error("Failed to create tenant database, rolling back...", e);
            tenantRepository.delete(savedTenant);
            throw new RuntimeException("Failed to create tenant: " + e.getMessage());
        }
    }

    private void createTenantDatabase(String databaseName) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // With auto-create=true, the database will be created on first connection
            // But we still need to ensure it exists
            String createDatabaseSQL = String.format(
                    "CREATE DATABASE IF NOT EXISTS `%s` " +
                            "CHARACTER SET utf8mb4 " +
                            "COLLATE utf8mb4_unicode_ci",
                    databaseName
            );

            statement.execute(createDatabaseSQL);
            log.info("Created/Verified database: {}", databaseName);

        } catch (Exception e) {
            log.error("Error creating database: {}", databaseName, e);
            throw new RuntimeException("Failed to create database: " + e.getMessage());
        }
    }

    private void initializeTenantDatabase(String databaseName) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("USE " + databaseName);

            // Create users table
            String createUsersTable = """
                CREATE TABLE IF NOT EXISTS users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    password VARCHAR(255) NOT NULL,
                    role VARCHAR(50) NOT NULL,
                    tenant_id VARCHAR(50) NOT NULL,
                    database_name VARCHAR(50),
                    full_name VARCHAR(255),
                    email VARCHAR(255),
                    phone VARCHAR(20),
                    is_enabled BOOLEAN DEFAULT true,
                    account_non_expired BOOLEAN DEFAULT true,
                    account_non_locked BOOLEAN DEFAULT true,
                    credentials_non_expired BOOLEAN DEFAULT true,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    
                    INDEX idx_username_tenant (username, tenant_id),
                    INDEX idx_tenant_id (tenant_id),
                    INDEX idx_role (role),
                    UNIQUE KEY uk_username_tenant (username, tenant_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

            statement.execute(createUsersTable);

            // Create demo_entities table
            String createDemoTable = """
                CREATE TABLE IF NOT EXISTS demo_entities (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    name VARCHAR(100) NOT NULL,
                    description TEXT,
                    value INT,
                    is_active BOOLEAN DEFAULT true,
                    category VARCHAR(50),
                    tenant_id VARCHAR(50) NOT NULL,
                    created_by VARCHAR(100),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    
                    INDEX idx_tenant_id (tenant_id),
                    INDEX idx_category (category),
                    INDEX idx_is_active (is_active),
                    INDEX idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

            statement.execute(createDemoTable);

            log.info("Initialized database: {}", databaseName);

        } catch (Exception e) {
            log.error("Error initializing database: {}", databaseName, e);
            throw new RuntimeException("Failed to initialize database: " + e.getMessage());
        }
    }

    private void createTenantAdminUser(Tenant tenant, String adminUsername, String adminPassword) {
        String originalTenant = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant("master");

        try {
            User adminUser = User.builder()
                    .username(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .role("TENANT_ADMIN")
                    .tenantId(tenant.getDatabaseName())
                    .databaseName(tenant.getDatabaseName())
                    .fullName(tenant.getName() + " Admin")
                    .email(tenant.getEmail())
                    .phone(tenant.getPhone())
                    .build();

            userRepository.save(adminUser);
            log.info("Created tenant admin user: {}", adminUsername);

        } finally {
            TenantContext.setCurrentTenant(originalTenant);
        }
    }

    public java.util.List<TenantResponse> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TenantResponse getTenantById(String id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant not found with id: " + id));
        return mapToResponse(tenant);
    }

    public TenantResponse getTenantByDatabaseName(String databaseName) {
        Tenant tenant = tenantRepository.findByDatabaseName(databaseName)
                .orElseThrow(() -> new RuntimeException("Tenant not found with database: " + databaseName));
        return mapToResponse(tenant);
    }

    private TenantResponse mapToResponse(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .databaseName(tenant.getDatabaseName())
                .domain(tenant.getDomain())
                .email(tenant.getEmail())
                .phone(tenant.getPhone())
                .address(tenant.getAddress())
                .status(tenant.getStatus())
                .adminUsername(tenant.getAdminUsername())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }
}