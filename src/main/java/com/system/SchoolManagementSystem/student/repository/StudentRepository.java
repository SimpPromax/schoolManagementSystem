package com.system.SchoolManagementSystem.student.repository;

import com.system.SchoolManagementSystem.student.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

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

    // OPTIMIZED: Add pagination to search
    @Query("SELECT s FROM Student s WHERE LOWER(s.fullName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Student> searchByNameOptimized(@Param("name") String name, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT s FROM Student s WHERE s.grade = :grade AND s.academicYear = :academicYear")
    List<Student> findByGradeAndAcademicYear(@Param("grade") String grade,
                                             @Param("academicYear") String academicYear);

    boolean existsByStudentId(String studentId);
    boolean existsByEmail(String email);

    // ========== OPTIMIZED QUERIES FOR TRANSACTION MATCHING ==========

    // Find students with pending amount near a specific amount
    @Query("SELECT s FROM Student s WHERE " +
            "s.pendingAmount BETWEEN :minAmount AND :maxAmount " +
            "ORDER BY ABS(s.pendingAmount - :amount)")
    List<Student> findByPendingAmountNear(@Param("amount") Double amount,
                                          @Param("minAmount") Double minAmount,
                                          @Param("maxAmount") Double maxAmount);

    // Find students with common school amounts
    @Query("SELECT s FROM Student s WHERE " +
            "s.pendingAmount IN :amounts OR " +
            "s.totalFee IN :amounts")
    List<Student> findByCommonAmounts(@Param("amounts") List<Double> amounts);

    // Find students by name parts for faster matching
    @Query("SELECT s FROM Student s WHERE " +
            "LOWER(s.fullName) LIKE LOWER(CONCAT(:namePart, '%')) " +
            "OR LOWER(s.fullName) LIKE LOWER(CONCAT('% ', :namePart, '%'))")
    List<Student> findByNamePart(@Param("namePart") String namePart);
}