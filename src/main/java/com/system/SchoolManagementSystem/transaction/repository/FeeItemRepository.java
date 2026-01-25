package com.system.SchoolManagementSystem.transaction.repository;

import com.system.SchoolManagementSystem.transaction.entity.FeeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeeItemRepository extends JpaRepository<FeeItem, Long> {
    List<FeeItem> findByFeeStructureId(Long feeStructureId);
}