package com.system.SchoolManagementSystem.student.repository;

import com.system.SchoolManagementSystem.student.entity.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {
    List<MedicalRecord> findByStudentId(Long studentId);
    List<MedicalRecord> findByStudentIdAndRecordType(Long studentId, MedicalRecord.RecordType recordType);
}