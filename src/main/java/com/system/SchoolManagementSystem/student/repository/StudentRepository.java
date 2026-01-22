package com.system.SchoolManagementSystem.student.repository;

import com.system.SchoolManagementSystem.student.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    // Original method - keep for backward compatibility
    Optional<Student> findByStudentId(String studentId);

    // Updated method - REMOVED DISTINCT keyword since we're using Set
    @Query("SELECT s FROM Student s " +
            "LEFT JOIN FETCH s.familyMembers " +
            "LEFT JOIN FETCH s.medicalRecords " +
            "LEFT JOIN FETCH s.achievements " +
            "LEFT JOIN FETCH s.interests " +
            "WHERE s.studentId = :studentId")
    Optional<Student> findByStudentIdWithRelations(@Param("studentId") String studentId);

    // Original method - keep for backward compatibility
    Optional<Student> findByEmail(String email);

    // Updated method - REMOVED DISTINCT keyword since we're using Set
    @Query("SELECT s FROM Student s " +
            "LEFT JOIN FETCH s.familyMembers " +
            "LEFT JOIN FETCH s.medicalRecords " +
            "LEFT JOIN FETCH s.achievements " +
            "LEFT JOIN FETCH s.interests " +
            "WHERE s.email = :email")
    Optional<Student> findByEmailWithRelations(@Param("email") String email);

    // Original method - keep for backward compatibility
    List<Student> findByGrade(String grade);

    // Updated method - REMOVED DISTINCT keyword since we're using Set
    @Query("SELECT s FROM Student s " +
            "LEFT JOIN FETCH s.familyMembers " +
            "LEFT JOIN FETCH s.medicalRecords " +
            "LEFT JOIN FETCH s.achievements " +
            "LEFT JOIN FETCH s.interests " +
            "WHERE s.grade = :grade")
    List<Student> findByGradeWithRelations(@Param("grade") String grade);

    List<Student> findByAcademicYear(String academicYear);
    List<Student> findByStatus(Student.StudentStatus status);

    @Query("SELECT s FROM Student s WHERE LOWER(s.fullName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Student> searchByName(@Param("name") String name);

    @Query("SELECT s FROM Student s WHERE s.grade = :grade AND s.academicYear = :academicYear")
    List<Student> findByGradeAndAcademicYear(@Param("grade") String grade,
                                             @Param("academicYear") String academicYear);

    boolean existsByStudentId(String studentId);
    boolean existsByEmail(String email);


    // UPDATED: Changed BigDecimal to Double for consistency
    @Query("SELECT COALESCE(SUM(s.totalFee), 0.0) FROM Student s")
    Double getTotalFeeSum();

    @Query("SELECT s.feeStatus, COUNT(s) FROM Student s GROUP BY s.feeStatus")
    List<Object[]> countStudentsByFeeStatus();

    @Query("SELECT COUNT(s) FROM Student s WHERE s.paidAmount > 0 AND s.paidAmount < s.totalFee")
    Long countPartialPayments();

    // UPDATED: Changed COALESCE(..., 0) to COALESCE(..., 0.0) for Double return
    @Query("SELECT CASE " +
            "WHEN s.pendingAmount <= 1000 THEN '0-1K' " +
            "WHEN s.pendingAmount <= 5000 THEN '1K-5K' " +
            "WHEN s.pendingAmount <= 10000 THEN '5K-10K' " +
            "WHEN s.pendingAmount <= 20000 THEN '10K-20K' " +
            "ELSE '20K+' " +
            "END as range, " +
            "COUNT(s) as count, " +
            "COALESCE(SUM(s.pendingAmount), 0.0) as amount " +
            "FROM Student s " +
            "WHERE s.feeStatus = 'OVERDUE' " +
            "GROUP BY CASE " +
            "WHEN s.pendingAmount <= 1000 THEN '0-1K' " +
            "WHEN s.pendingAmount <= 5000 THEN '1K-5K' " +
            "WHEN s.pendingAmount <= 10000 THEN '5K-10K' " +
            "WHEN s.pendingAmount <= 20000 THEN '10K-20K' " +
            "ELSE '20K+' " +
            "END")
    List<Object[]> getOverdueDistribution();

    @Query("SELECT s FROM Student s WHERE s.feeStatus = 'OVERDUE' AND s.feeDueDate <= :date")
    List<Student> findOverdueStudents(@Param("date") LocalDate date);
}