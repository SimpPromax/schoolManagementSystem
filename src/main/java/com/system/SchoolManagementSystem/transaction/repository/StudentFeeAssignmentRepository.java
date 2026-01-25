package com.system.SchoolManagementSystem.transaction.repository;

import com.system.SchoolManagementSystem.transaction.entity.StudentFeeAssignment;
import com.system.SchoolManagementSystem.transaction.enums.FeeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentFeeAssignmentRepository extends JpaRepository<StudentFeeAssignment, Long> {

    // Basic CRUD operations
    List<StudentFeeAssignment> findByStudentId(Long studentId);

    List<StudentFeeAssignment> findByStudentIdAndIsActive(Long studentId, Boolean isActive);

    Optional<StudentFeeAssignment> findByStudentIdAndFeeStructureIdAndAcademicYear(
            Long studentId, Long feeStructureId, String academicYear);

    List<StudentFeeAssignment> findByFeeStatus(FeeStatus feeStatus);

    // NEW: Find by academic year
    List<StudentFeeAssignment> findByAcademicYear(String academicYear);

    // NEW: Find by student and academic year
    @Query("SELECT sfa FROM StudentFeeAssignment sfa WHERE sfa.student.id = :studentId AND sfa.academicYear = :academicYear")
    Optional<StudentFeeAssignment> findByStudentIdAndAcademicYear(@Param("studentId") Long studentId,
                                                                  @Param("academicYear") String academicYear);

    // NEW: Find by academic year and status
    List<StudentFeeAssignment> findByAcademicYearAndFeeStatus(String academicYear, FeeStatus feeStatus);

    // NEW: Find by due date range
    @Query("SELECT sfa FROM StudentFeeAssignment sfa WHERE sfa.dueDate BETWEEN :startDate AND :endDate")
    List<StudentFeeAssignment> findByDueDateBetween(@Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate);

    // NEW: Find overdue assignments
    @Query("SELECT sfa FROM StudentFeeAssignment sfa WHERE sfa.dueDate < :today AND sfa.feeStatus != 'PAID'")
    List<StudentFeeAssignment> findOverdueAssignments(@Param("today") LocalDate today);

    // NEW: Find assignments due in next X days
    @Query("SELECT sfa FROM StudentFeeAssignment sfa WHERE sfa.dueDate BETWEEN :startDate AND :endDate AND sfa.feeStatus IN ('PENDING', 'PARTIAL')")
    List<StudentFeeAssignment> findDueSoon(@Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);

    // Status-based queries
    @Query("SELECT sfa FROM StudentFeeAssignment sfa WHERE " +
            "sfa.student.id = :studentId AND " +
            "sfa.isActive = true AND " +
            "sfa.feeStatus IN :statuses")
    List<StudentFeeAssignment> findActiveByStudentAndStatus(
            @Param("studentId") Long studentId,
            @Param("statuses") List<FeeStatus> statuses);

    // NEW: Find by student and multiple statuses
    @Query("SELECT sfa FROM StudentFeeAssignment sfa WHERE " +
            "sfa.student.id = :studentId AND " +
            "sfa.feeStatus IN :statuses")
    List<StudentFeeAssignment> findByStudentIdAndFeeStatusIn(@Param("studentId") Long studentId,
                                                             @Param("statuses") List<FeeStatus> statuses);

    // Aggregation queries
    @Query("SELECT COALESCE(SUM(sfa.paidAmount), 0) FROM StudentFeeAssignment sfa WHERE sfa.student.id = :studentId")
    Double getTotalPaidAmountByStudent(@Param("studentId") Long studentId);

    // NEW: Get total pending amount by student
    @Query("SELECT COALESCE(SUM(sfa.pendingAmount), 0) FROM StudentFeeAssignment sfa WHERE sfa.student.id = :studentId")
    Double getTotalPendingAmountByStudent(@Param("studentId") Long studentId);

    // NEW: Get total amount by student
    @Query("SELECT COALESCE(SUM(sfa.totalAmount), 0) FROM StudentFeeAssignment sfa WHERE sfa.student.id = :studentId")
    Double getTotalAmountByStudent(@Param("studentId") Long studentId);

    // NEW: Statistics by academic year
    @Query("SELECT sfa.academicYear, COUNT(sfa), COALESCE(SUM(sfa.totalAmount), 0), " +
            "COALESCE(SUM(sfa.paidAmount), 0), COALESCE(SUM(sfa.pendingAmount), 0) " +
            "FROM StudentFeeAssignment sfa " +
            "GROUP BY sfa.academicYear")
    List<Object[]> getStatisticsByAcademicYear();

    // NEW: Statistics by fee status
    @Query("SELECT sfa.feeStatus, COUNT(sfa), COALESCE(SUM(sfa.totalAmount), 0), " +
            "COALESCE(SUM(sfa.paidAmount), 0), COALESCE(SUM(sfa.pendingAmount), 0) " +
            "FROM StudentFeeAssignment sfa " +
            "GROUP BY sfa.feeStatus")
    List<Object[]> getStatisticsByFeeStatus();

    // FIXED: Statistics by grade - changed s.student.grade to s.grade
    @Query("SELECT s.grade, COUNT(sfa), COALESCE(SUM(sfa.totalAmount), 0), " +
            "COALESCE(SUM(sfa.paidAmount), 0), COALESCE(SUM(sfa.pendingAmount), 0) " +
            "FROM StudentFeeAssignment sfa " +
            "JOIN sfa.student s " +
            "GROUP BY s.grade")
    List<Object[]> getStatisticsByGrade();

    // NEW: Find assignments with pending amount
    @Query("SELECT sfa FROM StudentFeeAssignment sfa WHERE sfa.pendingAmount > 0 AND sfa.isActive = true")
    List<StudentFeeAssignment> findAssignmentsWithPendingAmount();

    // NEW: Find assignments by student and pending amount
    @Query("SELECT sfa FROM StudentFeeAssignment sfa WHERE sfa.student.id = :studentId AND sfa.pendingAmount > 0 AND sfa.isActive = true")
    List<StudentFeeAssignment> findPendingAssignmentsByStudent(@Param("studentId") Long studentId);

    // NEW: Count assignments by student and status
    @Query("SELECT COUNT(sfa) FROM StudentFeeAssignment sfa WHERE sfa.student.id = :studentId AND sfa.feeStatus = :status")
    Long countByStudentIdAndFeeStatus(@Param("studentId") Long studentId,
                                      @Param("status") FeeStatus status);

    // NEW: Get latest assignment for student
    @Query("SELECT sfa FROM StudentFeeAssignment sfa WHERE sfa.student.id = :studentId ORDER BY sfa.createdAt DESC")
    Optional<StudentFeeAssignment> findLatestByStudentId(@Param("studentId") Long studentId);

    // NEW: Find by student and active status
    List<StudentFeeAssignment> findByStudentIdAndIsActiveTrue(Long studentId);

    // NEW: Find by fee structure
    List<StudentFeeAssignment> findByFeeStructureId(Long feeStructureId);

    // NEW: Find by fee structure and academic year
    List<StudentFeeAssignment> findByFeeStructureIdAndAcademicYear(Long feeStructureId, String academicYear);

    // NEW: Bulk operations
    @Query("SELECT sfa FROM StudentFeeAssignment sfa WHERE sfa.student.id IN :studentIds AND sfa.isActive = true")
    List<StudentFeeAssignment> findByStudentIds(@Param("studentIds") List<Long> studentIds);

    // NEW: Find assignments needing reminders
    @Query("SELECT sfa FROM StudentFeeAssignment sfa WHERE " +
            "sfa.dueDate <= :reminderDate AND " +
            "sfa.feeStatus IN ('PENDING', 'PARTIAL', 'OVERDUE') AND " +
            "(sfa.lastReminderDate IS NULL OR sfa.lastReminderDate < :reminderDate)")
    List<StudentFeeAssignment> findAssignmentsNeedingReminders(@Param("reminderDate") LocalDate reminderDate);

    // NEW: Get total statistics
    @Query("SELECT " +
            "COUNT(sfa) as totalAssignments, " +
            "COUNT(CASE WHEN sfa.feeStatus = 'PAID' THEN 1 END) as paidCount, " +
            "COUNT(CASE WHEN sfa.feeStatus = 'PENDING' THEN 1 END) as pendingCount, " +
            "COUNT(CASE WHEN sfa.feeStatus = 'PARTIAL' THEN 1 END) as partialCount, " +
            "COUNT(CASE WHEN sfa.feeStatus = 'OVERDUE' THEN 1 END) as overdueCount, " +
            "COALESCE(SUM(sfa.totalAmount), 0) as totalAmount, " +
            "COALESCE(SUM(sfa.paidAmount), 0) as totalPaid, " +
            "COALESCE(SUM(sfa.pendingAmount), 0) as totalPending " +
            "FROM StudentFeeAssignment sfa " +
            "WHERE sfa.isActive = true")
    Object[] getOverallStatistics();

    // NEW: Check if assignment exists for student and term
    @Query("SELECT CASE WHEN COUNT(sfa) > 0 THEN TRUE ELSE FALSE END " +
            "FROM StudentFeeAssignment sfa " +
            "WHERE sfa.student.id = :studentId AND sfa.academicYear = :academicYear")
    Boolean existsByStudentIdAndAcademicYear(@Param("studentId") Long studentId,
                                             @Param("academicYear") String academicYear);

    // NEW: Find assignments with last payment before date
    @Query("SELECT sfa FROM StudentFeeAssignment sfa WHERE sfa.lastPaymentDate < :date AND sfa.feeStatus != 'PAID'")
    List<StudentFeeAssignment> findWithNoRecentPayment(@Param("date") LocalDate date);

    // FIXED: Get student fee summary - removed sfa.student.student
    @Query("SELECT " +
            "s.id, " +
            "s.fullName, " +
            "s.grade, " +
            "SUM(sfa.totalAmount) as totalFee, " +
            "SUM(sfa.paidAmount) as totalPaid, " +
            "SUM(sfa.pendingAmount) as totalPending, " +
            "MAX(sfa.dueDate) as latestDueDate " +
            "FROM StudentFeeAssignment sfa " +
            "JOIN sfa.student s " +
            "WHERE sfa.isActive = true " +
            "GROUP BY s.id, s.fullName, s.grade " +
            "HAVING SUM(sfa.pendingAmount) > 0")
    List<Object[]> getStudentFeeSummary();

    // NEW: Find active assignments
    List<StudentFeeAssignment> findByIsActiveTrue();

    // NEW: Find by student and isActive and feeStatus
    List<StudentFeeAssignment> findByStudentIdAndIsActiveTrueAndFeeStatus(Long studentId, FeeStatus feeStatus);

    // NEW: Get total collected fees by date range
    @Query("SELECT COALESCE(SUM(sfa.paidAmount), 0) FROM StudentFeeAssignment sfa WHERE sfa.lastPaymentDate BETWEEN :startDate AND :endDate")
    Double getCollectedFeesBetweenDates(@Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    // NEW: Count active assignments by academic year
    @Query("SELECT sfa.academicYear, COUNT(sfa) FROM StudentFeeAssignment sfa WHERE sfa.isActive = true GROUP BY sfa.academicYear")
    List<Object[]> countActiveAssignmentsByAcademicYear();

    // NEW: Find assignments with discounts
    @Query("SELECT sfa FROM StudentFeeAssignment sfa WHERE sfa.discountAmount > 0")
    List<StudentFeeAssignment> findAssignmentsWithDiscounts();

    // NEW: Get fee collection trend
    @Query(value = "SELECT DATE(sfa.last_payment_date) as payment_date, " +
            "COUNT(sfa.id) as assignment_count, " +
            "COALESCE(SUM(sfa.paid_amount), 0) as collected_amount " +
            "FROM student_fee_assignments sfa " +
            "WHERE sfa.last_payment_date IS NOT NULL " +
            "AND sfa.last_payment_date >= DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY) " +
            "GROUP BY DATE(sfa.last_payment_date) " +
            "ORDER BY payment_date DESC",
            nativeQuery = true)
    List<Object[]> getFeeCollectionTrend();
}