package com.system.SchoolManagementSystem.fee.repository;

import com.system.SchoolManagementSystem.fee.entity.FeeDashboardCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FeeDashboardCacheRepository extends JpaRepository<FeeDashboardCache, String> {

    Optional<FeeDashboardCache> findByCacheKey(String cacheKey);

    List<FeeDashboardCache> findByCacheType(String cacheType);

    @Query("SELECT fdc FROM FeeDashboardCache fdc WHERE fdc.expiresAt < :now")
    List<FeeDashboardCache> findExpiredCache(@Param("now") LocalDateTime now);

    @Query("SELECT fdc FROM FeeDashboardCache fdc WHERE fdc.dataDate = :date AND fdc.cacheType = :cacheType")
    Optional<FeeDashboardCache> findByDateAndType(@Param("date") LocalDate date,
                                                  @Param("cacheType") String cacheType);

    @Modifying
    @Query("DELETE FROM FeeDashboardCache fdc WHERE fdc.expiresAt < :now")
    void deleteExpiredCache(@Param("now") LocalDateTime now);
}