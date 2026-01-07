package com.system.SchoolManagementSystem.student.repository;

import com.system.SchoolManagementSystem.student.entity.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {
    List<FamilyMember> findByStudentId(Long studentId);
    List<FamilyMember> findByIsPrimaryContactTrue();
    List<FamilyMember> findByIsEmergencyContactTrue();
}