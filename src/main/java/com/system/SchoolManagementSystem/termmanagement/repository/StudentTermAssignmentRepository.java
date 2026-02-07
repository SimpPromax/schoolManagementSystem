package com.system.SchoolManagementSystem.termmanagement.repository;

import com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface StudentTermAssignmentRepository extends JpaRepository<StudentTermAssignment, Long> {

    // ========== NEW CRITICAL METHOD - FIXES THE TRANSACTION ISSUE ==========

    /**
     * Check if a student-term assignment exists (prevents transient object issues)
     * IMPORTANT: This method uses EXISTS query which is safe for checking without loading entities
     */
    @Query("SELECT CASE WHEN COUNT(sta) > 0 THEN true ELSE false END " +
            "FROM StudentTermAssignment sta " +
            "WHERE sta.student.id = :studentId AND sta.academicTerm.id = :academicTermId")
    boolean existsByStudentIdAndAcademicTermId(@Param("studentId") Long studentId,
                                               @Param("academicTermId") Long academicTermId);

    // ========== EXISTING METHODS (with fixes) ==========

    Optional<StudentTermAssignment> findByStudentIdAndAcademicTermId(Long studentId, Long academicTermId);

    List<StudentTermAssignment> findByStudentId(Long studentId);

    List<StudentTermAssignment> findByAcademicTermId(Long academicTermId);

    List<StudentTermAssignment> findByStudentIdAndTermFeeStatus(Long studentId, StudentTermAssignment.FeeStatus status);

    @Query("SELECT s FROM StudentTermAssignment s JOIN s.student st WHERE s.academicTerm.id = :termId AND st.grade = :grade")
    List<StudentTermAssignment> findByAcademicTermIdAndStudentGrade(@Param("termId") Long termId,
                                                                    @Param("grade") String grade);

    @Query("SELECT s FROM StudentTermAssignment s WHERE s.termFeeStatus = :status AND s.dueDate < :today")
    List<StudentTermAssignment> findOverdueAssignments(@Param("status") StudentTermAssignment.FeeStatus status,
                                                       @Param("today") LocalDate today);

    @Query("SELECT COUNT(s) FROM StudentTermAssignment s WHERE s.academicTerm.id = :termId")
    Long countByAcademicTermId(@Param("termId") Long termId);

    @Query("SELECT COUNT(s) FROM StudentTermAssignment s WHERE s.academicTerm.id = :termId AND s.termFeeStatus = :status")
    Long countByAcademicTermIdAndStatus(@Param("termId") Long termId,
                                        @Param("status") StudentTermAssignment.FeeStatus status);

    @Query("SELECT COALESCE(SUM(s.totalTermFee), 0) FROM StudentTermAssignment s WHERE s.academicTerm.id = :termId")
    Double getTotalExpectedRevenueForTerm(@Param("termId") Long termId);

    @Query("SELECT COALESCE(SUM(s.paidAmount), 0) FROM StudentTermAssignment s WHERE s.academicTerm.id = :termId")
    Double getTotalCollectedForTerm(@Param("termId") Long termId);

    @Query("SELECT COALESCE(SUM(s.pendingAmount), 0) FROM StudentTermAssignment s WHERE s.academicTerm.id = :termId")
    Double getTotalOutstandingForTerm(@Param("termId") Long termId);

    @Query("SELECT s FROM StudentTermAssignment s WHERE s.student.id = :studentId AND s.academicTerm.isCurrent = true")
    Optional<StudentTermAssignment> findCurrentTermAssignmentForStudent(@Param("studentId") Long studentId);

    @Query("SELECT s FROM StudentTermAssignment s WHERE s.studentFeeAssignment.id = :feeAssignmentId")
    Optional<StudentTermAssignment> findByFeeAssignmentId(@Param("feeAssignmentId") Long feeAssignmentId);

    @Query("SELECT s FROM StudentTermAssignment s WHERE s.academicTerm.id = :termId AND s.termFeeStatus IN ('PENDING', 'PARTIAL', 'OVERDUE')")
    List<StudentTermAssignment> findPendingAssignmentsForTerm(@Param("termId") Long termId);

    @Query("SELECT s FROM StudentTermAssignment s WHERE s.student.id = :studentId ORDER BY s.academicTerm.startDate DESC")
    List<StudentTermAssignment> findByStudentIdOrderByTermDateDesc(@Param("studentId") Long studentId);

    @Query("SELECT s FROM StudentTermAssignment s WHERE s.academicTerm.id = :termId AND s.termFeeStatus = 'PAID'")
    List<StudentTermAssignment> findPaidAssignmentsForTerm(@Param("termId") Long termId);

    @Query("SELECT s FROM StudentTermAssignment s WHERE s.student.id = :studentId AND s.academicTerm.academicYear = :academicYear")
    List<StudentTermAssignment> findByStudentIdAndAcademicYear(@Param("studentId") Long studentId,
                                                               @Param("academicYear") String academicYear);

    @Query("SELECT s FROM StudentTermAssignment s WHERE s.dueDate BETWEEN :startDate AND :endDate")
    List<StudentTermAssignment> findAssignmentsDueBetween(@Param("startDate") LocalDate startDate,
                                                          @Param("endDate") LocalDate endDate);

    @Query("SELECT s FROM StudentTermAssignment s WHERE s.academicTerm.academicYear = :academicYear")
    List<StudentTermAssignment> findByAcademicYear(@Param("academicYear") String academicYear);

    @Query("SELECT s FROM StudentTermAssignment s WHERE s.student.id = :studentId AND s.termFeeStatus = 'OVERDUE'")
    List<StudentTermAssignment> findOverdueAssignmentsForStudent(@Param("studentId") Long studentId);

    @Query("SELECT COUNT(s) FROM StudentTermAssignment s WHERE s.academicTerm.id = :termId AND s.isBilled = true")
    Long countBilledStudentsByTermId(@Param("termId") Long termId);

    @Query("SELECT COUNT(s) FROM StudentTermAssignment s WHERE s.academicTerm.id = :termId AND s.termFeeStatus IN ('PAID', 'PARTIAL')")
    Long countPaidOrPartialByTermId(@Param("termId") Long termId);

    @Query("SELECT DISTINCT s.academicTerm.academicYear FROM StudentTermAssignment s WHERE s.student.id = :studentId")
    List<String> findDistinctAcademicYearsByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT s FROM StudentTermAssignment s WHERE s.student.id = :studentId AND s.academicTerm.startDate >= :startDate")
    List<StudentTermAssignment> findByStudentIdAndTermsAfterDate(@Param("studentId") Long studentId,
                                                                 @Param("startDate") LocalDate startDate);

    @Query("SELECT s FROM StudentTermAssignment s WHERE s.academicTerm.id = :termId AND s.student.status = 'ACTIVE'")
    List<StudentTermAssignment> findActiveStudentAssignmentsForTerm(@Param("termId") Long termId);

    @Query("SELECT s FROM StudentTermAssignment s WHERE s.lastReminderDate < :date OR s.lastReminderDate IS NULL")
    List<StudentTermAssignment> findAssignmentsNeedingReminder(@Param("date") LocalDate date);

    @Query("SELECT s FROM StudentTermAssignment s WHERE s.student.grade = :grade AND s.academicTerm.isCurrent = true")
    List<StudentTermAssignment> findCurrentTermAssignmentsByGrade(@Param("grade") String grade);

    // ========== NEW BATCH QUERY METHODS ==========

    /**
     * Check if a student has any term assignments (single student)
     */
    @Query("SELECT COUNT(ta) > 0 FROM StudentTermAssignment ta WHERE ta.student.id = :studentId")
    boolean hasTermAssignments(@Param("studentId") Long studentId);

    /**
     * Count term assignments for a student (single student)
     */
    @Query("SELECT COUNT(ta) FROM StudentTermAssignment ta WHERE ta.student.id = :studentId")
    Integer countTermAssignments(@Param("studentId") Long studentId);

    /**
     * Batch check for multiple students - returns student IDs that have term assignments
     */
    @Query("SELECT ta.student.id FROM StudentTermAssignment ta " +
            "WHERE ta.student.id IN :studentIds " +
            "GROUP BY ta.student.id " +
            "HAVING COUNT(ta) > 0")
    List<Long> findStudentsWithTermAssignments(@Param("studentIds") Set<Long> studentIds);

    /**
     * Batch count term assignments for multiple students
     */
    @Query("SELECT ta.student.id as studentId, COUNT(ta) as assignmentCount " +
            "FROM StudentTermAssignment ta " +
            "WHERE ta.student.id IN :studentIds " +
            "GROUP BY ta.student.id")
    List<Object[]> batchCountTermAssignments(@Param("studentIds") Set<Long> studentIds);

    /**
     * Combined batch query - returns studentId, hasAssignments, assignmentCount
     */
    @Query("SELECT ta.student.id as studentId, " +
            "CASE WHEN COUNT(ta) > 0 THEN true ELSE false END as hasAssignments, " +
            "COUNT(ta) as assignmentCount " +
            "FROM StudentTermAssignment ta " +
            "WHERE ta.student.id IN :studentIds " +
            "GROUP BY ta.student.id")
    List<Object[]> batchGetTermAssignmentInfo(@Param("studentIds") Set<Long> studentIds);

    /**
     * Batch check for active term assignments (excluding cancelled/waived)
     */
    @Query("SELECT ta.student.id as studentId, " +
            "CASE WHEN COUNT(ta) > 0 THEN true ELSE false END as hasActiveAssignments, " +
            "COUNT(ta) as activeAssignmentCount " +
            "FROM StudentTermAssignment ta " +
            "WHERE ta.student.id IN :studentIds " +
            "AND ta.termFeeStatus NOT IN ('CANCELLED', 'WAIVED') " +
            "GROUP BY ta.student.id")
    List<Object[]> batchGetActiveTermAssignmentInfo(@Param("studentIds") Set<Long> studentIds);

    /**
     * Batch check for pending/overdue term assignments
     */
    @Query("SELECT ta.student.id as studentId, " +
            "CASE WHEN COUNT(ta) > 0 THEN true ELSE false END as hasPendingAssignments, " +
            "COUNT(ta) as pendingAssignmentCount, " +
            "SUM(ta.pendingAmount) as totalPendingAmount " +
            "FROM StudentTermAssignment ta " +
            "WHERE ta.student.id IN :studentIds " +
            "AND ta.termFeeStatus IN ('PENDING', 'PARTIAL', 'OVERDUE') " +
            "GROUP BY ta.student.id")
    List<Object[]> batchGetPendingTermAssignmentInfo(@Param("studentIds") Set<Long> studentIds);

    // ========== NEW QUERY METHODS FOR AUTO-BILLING ==========

    /**
     * Check if student is already billed for term (optimized version)
     */
    @Query("SELECT s FROM StudentTermAssignment s " +
            "WHERE s.student.id = :studentId " +
            "AND s.academicTerm.id = :termId " +
            "AND s.isBilled = true")
    Optional<StudentTermAssignment> findBilledAssignment(@Param("studentId") Long studentId,
                                                         @Param("termId") Long termId);

    /**
     * Find assignments by student and academic year with pending fees
     */
    @Query("SELECT s FROM StudentTermAssignment s " +
            "WHERE s.student.id = :studentId " +
            "AND s.academicTerm.academicYear = :academicYear " +
            "AND s.pendingAmount > 0 " +
            "ORDER BY s.dueDate ASC")
    List<StudentTermAssignment> findPendingAssignmentsByStudentAndYear(
            @Param("studentId") Long studentId,
            @Param("academicYear") String academicYear);

    /**
     * Get total pending amount for a student across all terms
     */
    @Query("SELECT COALESCE(SUM(s.pendingAmount), 0) FROM StudentTermAssignment s WHERE s.student.id = :studentId")
    Double getTotalPendingAmountForStudent(@Param("studentId") Long studentId);

    /**
     * Get assignments that need billing (no fee items yet)
     */
    @Query("SELECT s FROM StudentTermAssignment s WHERE s.isBilled = false OR s.feeItems IS EMPTY")
    List<StudentTermAssignment> findAssignmentsNeedingBilling();

    /**
     * Get assignments with overdue fees for reminder generation
     */
    @Query("SELECT s FROM StudentTermAssignment s " +
            "WHERE s.termFeeStatus IN ('PENDING', 'PARTIAL', 'OVERDUE') " +
            "AND s.dueDate IS NOT NULL " +
            "AND s.dueDate <= CURRENT_DATE " +
            "AND (s.lastReminderDate IS NULL OR s.lastReminderDate < :reminderCutoffDate)")
    List<StudentTermAssignment> findAssignmentsNeedingFeeReminder(
            @Param("reminderCutoffDate") LocalDate reminderCutoffDate);
}