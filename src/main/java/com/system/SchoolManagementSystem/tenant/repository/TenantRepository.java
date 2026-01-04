package com.system.SchoolManagementSystem.tenant.repository;

import com.system.SchoolManagementSystem.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findByDatabaseName(String databaseName);

    Optional<Tenant> findByDomain(String domain);

    @Query("SELECT COUNT(t) > 0 FROM Tenant t WHERE t.databaseName = :databaseName")
    boolean existsByDatabaseName(@Param("databaseName") String databaseName);

    @Query("SELECT COUNT(t) > 0 FROM Tenant t WHERE t.domain = :domain")
    boolean existsByDomain(@Param("domain") String domain);

    @Query("SELECT t FROM Tenant t WHERE t.status = 'ACTIVE'")
    java.util.List<Tenant> findAllActive();
}