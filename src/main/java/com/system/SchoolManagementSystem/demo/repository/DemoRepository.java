package com.system.SchoolManagementSystem.demo.repository;

import com.system.SchoolManagementSystem.demo.entity.DemoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DemoRepository extends JpaRepository<DemoEntity, Long> {

    List<DemoEntity> findByTenantId(String tenantId);

    List<DemoEntity> findByTenantIdAndIsActiveTrue(String tenantId);

    List<DemoEntity> findByTenantIdAndCategory(String tenantId, String category);

    @Query("SELECT d FROM DemoEntity d WHERE d.tenantId = :tenantId AND LOWER(d.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<DemoEntity> searchByTenantIdAndName(@Param("tenantId") String tenantId,
                                             @Param("search") String search);

    long countByTenantId(String tenantId);

    @Query("SELECT COUNT(d) FROM DemoEntity d WHERE d.tenantId = :tenantId AND d.isActive = true")
    long countActiveByTenantId(@Param("tenantId") String tenantId);

    @Query("SELECT DISTINCT d.category FROM DemoEntity d WHERE d.tenantId = :tenantId AND d.category IS NOT NULL")
    List<String> findDistinctCategoriesByTenantId(@Param("tenantId") String tenantId);
}