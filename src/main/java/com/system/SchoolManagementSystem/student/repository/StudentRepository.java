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

    @Query("SELECT s FROM Student s WHERE s.grade = :grade AND s.academicYear = :academicYear")
    List<Student> findByGradeAndAcademicYear(@Param("grade") String grade,
                                             @Param("academicYear") String academicYear);

    boolean existsByStudentId(String studentId);
    boolean existsByEmail(String email);
}