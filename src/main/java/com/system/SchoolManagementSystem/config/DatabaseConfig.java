package com.system.SchoolManagementSystem.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String masterUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Bean
    @Primary
    public DataSource dataSource() {
        // Create master data source
        HikariConfig masterConfig = new HikariConfig();
        masterConfig.setJdbcUrl(masterUrl);
        masterConfig.setUsername(username);
        masterConfig.setPassword(password);
        masterConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        masterConfig.setMaximumPoolSize(10);
        masterConfig.setMinimumIdle(2);
        masterConfig.setIdleTimeout(30000);
        masterConfig.setMaxLifetime(1800000);
        masterConfig.setConnectionTimeout(30000);
        masterConfig.setConnectionTestQuery("SELECT 1");
        masterConfig.setPoolName("MasterDataSource");

        // Additional properties for auto-creation
        masterConfig.addDataSourceProperty("createDatabaseIfNotExist", "true");
        masterConfig.addDataSourceProperty("allowPublicKeyRetrieval", "true");
        masterConfig.addDataSourceProperty("useSSL", "false");
        masterConfig.addDataSourceProperty("serverTimezone", "UTC");

        return new HikariDataSource(masterConfig);
    }

    public DataSource createTenantDataSource(String databaseName) {
        // Create URL for tenant database
        String tenantUrl = "jdbc:mysql://localhost:3306/" + databaseName +
                "?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

        HikariConfig tenantConfig = new HikariConfig();
        tenantConfig.setJdbcUrl(tenantUrl);
        tenantConfig.setUsername(username);
        tenantConfig.setPassword(password);
        tenantConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        tenantConfig.setMaximumPoolSize(5);
        tenantConfig.setMinimumIdle(1);
        tenantConfig.setPoolName("TenantDataSource-" + databaseName);
        tenantConfig.setConnectionTestQuery("SELECT 1");

        return new HikariDataSource(tenantConfig);
    }
}