package com.system.SchoolManagementSystem.student.dto;

import com.system.SchoolManagementSystem.student.entity.MedicalRecord;
import lombok.Data;

@Data
public class MedicalRecordCreateDTO {
    private MedicalRecord.RecordType recordType;
    private String name;
    private MedicalRecord.Severity severity;
    private String notes;
    private String frequency;
    private String prescribedBy;
}