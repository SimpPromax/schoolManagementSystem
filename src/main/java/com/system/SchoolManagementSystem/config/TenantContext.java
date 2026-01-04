package com.system.SchoolManagementSystem.config;

public class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_DATABASE = new ThreadLocal<>();
    private static final String DEFAULT_TENANT = "master";
    private static final String DEFAULT_DATABASE = "school_master";

    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getCurrentTenant() {
        String tenant = CURRENT_TENANT.get();
        return tenant != null ? tenant : DEFAULT_TENANT;
    }

    public static void setCurrentDatabase(String databaseName) {
        CURRENT_DATABASE.set(databaseName);
    }

    public static String getCurrentDatabase() {
        String database = CURRENT_DATABASE.get();
        return database != null ? database : DEFAULT_DATABASE;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_DATABASE.remove();
    }
}