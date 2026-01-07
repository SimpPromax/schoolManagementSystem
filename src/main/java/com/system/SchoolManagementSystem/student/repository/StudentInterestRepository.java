package com.system.SchoolManagementSystem.student.repository;

import com.system.SchoolManagementSystem.student.entity.StudentInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentInterestRepository extends JpaRepository<StudentInterest, Long> {
    List<StudentInterest> findByStudentId(Long studentId);
    List<StudentInterest> findByStudentIdAndInterestType(Long studentId, StudentInterest.InterestType interestType);
}