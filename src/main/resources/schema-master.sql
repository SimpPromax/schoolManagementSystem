-- ============================================
-- School Management System - Master Database Setup
-- ============================================
-- Run this script as MySQL root user
-- Command: mysql -u root -p < schema-master.sql
-- ============================================

-- 1. Create master database
CREATE DATABASE IF NOT EXISTS school_master
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

-- 2. Create application user with strong password
-- First, drop if exists to avoid conflicts
DROP USER IF EXISTS 'school_app'@'localhost';

CREATE USER 'school_app'@'localhost' IDENTIFIED BY 'SchoolApp@Secure2024!';

-- 3. Grant necessary privileges
-- Full access to school_master database
GRANT ALL PRIVILEGES ON school_master.* TO 'school_app'@'localhost';

-- Grant CREATE/DROP DATABASE privilege for multi-tenancy
GRANT CREATE, DROP ON *.* TO 'school_app'@'localhost';

-- Grant necessary privileges for all tenant databases (they will start with 'school_')
GRANT ALL PRIVILEGES ON `school\_%`.* TO 'school_app'@'localhost';

-- 4. Optional: Grant file privilege for backup/restore operations
GRANT FILE ON *.* TO 'school_app'@'localhost';

-- 5. Reload privileges to apply changes
FLUSH PRIVILEGES;

USE school_master;

-- ============================================
-- Create Tables
-- ============================================

-- Tenants table
CREATE TABLE IF NOT EXISTS tenants (
    id VARCHAR(36) PRIMARY KEY,
    database_name VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    domain VARCHAR(255) UNIQUE,
    email VARCHAR(255),
    phone VARCHAR(20),
    address TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    admin_username VARCHAR(100),
    admin_password VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT tenants_database_name_check
    CHECK (database_name REGEXP '^[a-z0-9_]+$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Users table (master + tenant users)
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Demo entities table
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Insert Initial Data
-- ============================================

-- Insert super admin user (password: admin123 - will be hashed by Spring)
-- Note: In production, this should be inserted by the application
INSERT INTO users (username, password, role, tenant_id, database_name, full_name, email, phone) VALUES
('superadmin', '$2a$12$YourBcryptHashHere', 'ADMIN', 'master', 'school_master', 'Super Administrator', 'admin@schoolsystem.com', '+1234567890');

-- Insert sample tenant for testing
INSERT INTO tenants (id, database_name, name, email, status) VALUES
(UUID(), 'sample_school', 'Sample School', 'sample@school.com', 'ACTIVE');

-- ============================================
-- Create Stored Procedures for Tenant Management
-- ============================================

DELIMITER //

-- Procedure to create a new tenant database
CREATE PROCEDURE CreateTenantDatabase(IN db_name VARCHAR(50))
BEGIN
    DECLARE sql_stmt VARCHAR(1000);

    -- Create database
    SET @sql_stmt = CONCAT('CREATE DATABASE IF NOT EXISTS `', db_name, '` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');
    PREPARE stmt FROM @sql_stmt;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    -- Grant privileges to school_app user
    SET @sql_stmt = CONCAT('GRANT ALL PRIVILEGES ON `', db_name, '`.* TO ''school_app''@''localhost''');
    PREPARE stmt FROM @sql_stmt;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SELECT CONCAT('Database ', db_name, ' created successfully') as result;
END//

-- Procedure to initialize tenant database schema
CREATE PROCEDURE InitializeTenantDatabase(IN db_name VARCHAR(50))
BEGIN
    DECLARE sql_stmt TEXT;

    -- Switch to tenant database
    SET @sql_stmt = CONCAT('USE `', db_name, '`');
    PREPARE stmt FROM @sql_stmt;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    -- Create users table (same as master)
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    -- Create students table
    CREATE TABLE IF NOT EXISTS students (
        id BIGINT PRIMARY KEY AUTO_INCREMENT,
        student_code VARCHAR(20) UNIQUE,
        first_name VARCHAR(100) NOT NULL,
        last_name VARCHAR(100) NOT NULL,
        date_of_birth DATE,
        gender ENUM('MALE', 'FEMALE', 'OTHER'),
        address TEXT,
        parent_name VARCHAR(200),
        parent_phone VARCHAR(20),
        enrollment_date DATE,
        status ENUM('ACTIVE', 'INACTIVE', 'GRADUATED', 'TRANSFERRED') DEFAULT 'ACTIVE',
        photo_url VARCHAR(500),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

        INDEX idx_student_code (student_code),
        INDEX idx_status (status),
        INDEX idx_enrollment_date (enrollment_date)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    -- Create demo_entities table
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    SELECT CONCAT('Database ', db_name, ' initialized successfully') as result;
END//

DELIMITER ;

-- ============================================
-- Create Views for Reporting
-- ============================================

-- View to see all active tenants
CREATE OR REPLACE VIEW vw_active_tenants AS
SELECT
    id,
    name,
    database_name,
    domain,
    email,
    status,
    created_at,
    updated_at
FROM tenants
WHERE status = 'ACTIVE'
ORDER BY created_at DESC;

-- View to see user statistics by tenant
CREATE OR REPLACE VIEW vw_user_statistics AS
SELECT
    tenant_id,
    COUNT(*) as total_users,
    SUM(CASE WHEN role = 'ADMIN' THEN 1 ELSE 0 END) as admin_count,
    SUM(CASE WHEN role = 'TEACHER' THEN 1 ELSE 0 END) as teacher_count,
    SUM(CASE WHEN role = 'STUDENT' THEN 1 ELSE 0 END) as student_count,
    SUM(CASE WHEN role = 'USER' THEN 1 ELSE 0 END) as user_count,
    SUM(CASE WHEN is_enabled = FALSE THEN 1 ELSE 0 END) as disabled_count
FROM users
GROUP BY tenant_id
ORDER BY total_users DESC;

-- ============================================
-- Create Triggers for Audit
-- ============================================

-- Trigger to update tenant updated_at timestamp
DELIMITER //

CREATE TRIGGER trg_tenants_update_timestamp
BEFORE UPDATE ON tenants
FOR EACH ROW
BEGIN
    SET NEW.updated_at = CURRENT_TIMESTAMP;
END//

CREATE TRIGGER trg_users_update_timestamp
BEFORE UPDATE ON users
FOR EACH ROW
BEGIN
    SET NEW.updated_at = CURRENT_TIMESTAMP;
END//

DELIMITER ;

-- ============================================
-- Create Indexes for Performance
-- ============================================

CREATE INDEX idx_tenants_status ON tenants(status);
CREATE INDEX idx_tenants_created_at ON tenants(created_at);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_demo_entities_tenant_category ON demo_entities(tenant_id, category);

-- ============================================
-- Verification Queries
-- ============================================

-- Show all tables created
SHOW TABLES;

-- Show user privileges
SHOW GRANTS FOR 'school_app'@'localhost';

-- Count records in each table
SELECT 'tenants' as table_name, COUNT(*) as record_count FROM tenants
UNION ALL
SELECT 'users', COUNT(*) FROM users
UNION ALL
SELECT 'demo_entities', COUNT(*) FROM demo_entities;

-- ============================================
-- Success Message
-- ============================================

SELECT '============================================' as '';
SELECT 'School Management System Database Setup' as '';
SELECT '============================================' as '';
SELECT '✅ Master database created: school_master' as '';
SELECT '✅ Application user created: school_app@localhost' as '';
SELECT '✅ Tables created successfully' as '';
SELECT '✅ Stored procedures created for tenant management' as '';
SELECT '✅ Database setup completed!' as '';
SELECT '============================================' as '';
SELECT '' as '';
SELECT 'Next Steps:' as '';
SELECT '1. Update application.properties with:' as '';
SELECT '   spring.datasource.username=school_app' as '';
SELECT '   spring.datasource.password=SchoolApp@Secure2024!' as '';
SELECT '2. Run the Spring Boot application' as '';
SELECT '3. Access API at: http://localhost:8080/api' as '';
SELECT '============================================' as '';