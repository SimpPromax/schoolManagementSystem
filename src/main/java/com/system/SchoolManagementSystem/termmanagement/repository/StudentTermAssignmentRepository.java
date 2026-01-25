package com.system.SchoolManagementSystem.termmanagement.repository;

import com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentTermAssignmentRepository extends JpaRepository<StudentTermAssignment, Long> {

    Optional<StudentTermAssignment> findByStudentIdAndAcademicTermId(Long studentId, Long academicTermId);

    List<StudentTermAssignment> findByStudentId(Long studentId);

    List<StudentTermAssignment> findByAcademicTermId(Long academicTermId);

    List<StudentTermAssignment> findByStudentIdAndTermFeeStatus(Long studentId, StudentTermAssignment.FeeStatus status);

    // FIXED METHOD: Added JOIN to Student entity to access grade field
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

    // Additional methods for improved functionality

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
}