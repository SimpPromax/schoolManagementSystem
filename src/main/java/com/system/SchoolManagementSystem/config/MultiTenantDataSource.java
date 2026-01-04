package com.system.SchoolManagementSystem.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Component
public class MultiTenantDataSource extends AbstractRoutingDataSource {

    private final Map<Object, Object> targetDataSources = new HashMap<>();

    public MultiTenantDataSource() {
        setTargetDataSources(targetDataSources);
        setLenientFallback(false);
    }

    public void addDataSource(String tenantId, DataSource dataSource) {
        targetDataSources.put(tenantId, dataSource);
        setTargetDataSources(targetDataSources);
        afterPropertiesSet(); // Re-initialize
    }

    public void removeDataSource(String tenantId) {
        targetDataSources.remove(tenantId);
        setTargetDataSources(targetDataSources);
        afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        String databaseName = TenantContext.getCurrentDatabase();

        if (databaseName != null && !databaseName.equals("school_master")) {
            return databaseName;
        }

        return null; // Use default (master) data source
    }

    @Override
    protected DataSource determineTargetDataSource() {
        Object lookupKey = determineCurrentLookupKey();

        if (lookupKey == null) {
            return super.determineTargetDataSource();
        }

        DataSource dataSource = (DataSource) this.targetDataSources.get(lookupKey);
        if (dataSource == null) {
            // Create new data source for tenant if not exists
            dataSource = createDataSourceForTenant(lookupKey.toString());
            addDataSource(lookupKey.toString(), dataSource);
        }

        return dataSource;
    }

    private DataSource createDataSourceForTenant(String databaseName) {
        // You'll need to inject DatabaseConfig or create a new instance
        // For now, return null - this will be implemented when DatabaseConfig is available
        return null;
    }
}