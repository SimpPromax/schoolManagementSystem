package com.system.SchoolManagementSystem.transaction.repository;

import com.system.SchoolManagementSystem.transaction.entity.FeeStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeeStructureRepository extends JpaRepository<FeeStructure, Long> {

    Optional<FeeStructure> findByGradeAndAcademicYear(String grade, String academicYear);

    List<FeeStructure> findByGrade(String grade);

    List<FeeStructure> findByAcademicYear(String academicYear);

    List<FeeStructure> findByIsActive(Boolean isActive);
}