package com.system.SchoolManagementSystem.transaction.repository;

import com.system.SchoolManagementSystem.transaction.entity.FeeInstallment;
import com.system.SchoolManagementSystem.transaction.enums.FeeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FeeInstallmentRepository extends JpaRepository<FeeInstallment, Long> {

    List<FeeInstallment> findByFeeAssignmentId(Long feeAssignmentId);

    List<FeeInstallment> findByFeeAssignmentStudentId(Long studentId);

    List<FeeInstallment> findByStatus(FeeStatus status);

    List<FeeInstallment> findByDueDateBeforeAndStatus(LocalDate date, FeeStatus status);

    List<FeeInstallment> findByDueDateBetween(LocalDate startDate, LocalDate endDate);
}