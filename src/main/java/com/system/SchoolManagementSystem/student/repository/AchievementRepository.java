package com.system.SchoolManagementSystem.student.repository;

import com.system.SchoolManagementSystem.student.entity.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AchievementRepository extends JpaRepository<Achievement, Long> {
    List<Achievement> findByStudentId(Long studentId);
    List<Achievement> findByStudentIdAndType(Long studentId, Achievement.AchievementType type);
    List<Achievement> findByStudentIdAndYear(Long studentId, Integer year);
}