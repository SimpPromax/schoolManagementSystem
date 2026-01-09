package com.system.SchoolManagementSystem.transaction.repository;

import com.system.SchoolManagementSystem.transaction.entity.StudentFeeAssignment;
import com.system.SchoolManagementSystem.transaction.enums.FeeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentFeeAssignmentRepository extends JpaRepository<StudentFeeAssignment, Long> {

    List<StudentFeeAssignment> findByStudentId(Long studentId);

    List<StudentFeeAssignment> findByStudentIdAndIsActive(Long studentId, Boolean isActive);

    Optional<StudentFeeAssignment> findByStudentIdAndFeeStructureIdAndAcademicYear(
            Long studentId, Long feeStructureId, String academicYear);

    List<StudentFeeAssignment> findByFeeStatus(FeeStatus feeStatus);

    @Query("SELECT sfa FROM StudentFeeAssignment sfa WHERE " +
            "sfa.student.id = :studentId AND " +
            "sfa.isActive = true AND " +
            "sfa.feeStatus IN :statuses")
    List<StudentFeeAssignment> findActiveByStudentAndStatus(
            @Param("studentId") Long studentId,
            @Param("statuses") List<FeeStatus> statuses);

    @Query("SELECT COALESCE(SUM(sfa.paidAmount), 0) FROM StudentFeeAssignment sfa WHERE sfa.student.id = :studentId")
    Double getTotalPaidAmountByStudent(@Param("studentId") Long studentId);
}