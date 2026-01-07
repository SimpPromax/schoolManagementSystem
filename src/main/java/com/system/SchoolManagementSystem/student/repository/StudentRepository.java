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
    Optional<Student> findByStudentId(String studentId);
    Optional<Student> findByEmail(String email);

    List<Student> findByGrade(String grade);
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