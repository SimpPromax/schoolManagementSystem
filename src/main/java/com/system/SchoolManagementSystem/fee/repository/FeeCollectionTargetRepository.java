package com.system.SchoolManagementSystem.fee.repository;

import com.system.SchoolManagementSystem.fee.entity.FeeCollectionTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FeeCollectionTargetRepository extends JpaRepository<FeeCollectionTarget, Long> {

    List<FeeCollectionTarget> findByStatus(String status);

    List<FeeCollectionTarget> findByTargetTypeAndStatus(String targetType, String status);

    @Query("SELECT f FROM FeeCollectionTarget f WHERE f.startDate <= :date AND f.endDate >= :date AND f.status = 'ACTIVE'")
    List<FeeCollectionTarget> findActiveTargetsForDate(@Param("date") LocalDate date);

    @Query("SELECT SUM(f.collectedAmount) FROM FeeCollectionTarget f WHERE f.startDate >= :startDate AND f.endDate <= :endDate")
    Double getTotalCollectedBetweenDates(@Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(f.targetAmount) FROM FeeCollectionTarget f WHERE f.startDate >= :startDate AND f.endDate <= :endDate")
    Double getTotalTargetBetweenDates(@Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);
}