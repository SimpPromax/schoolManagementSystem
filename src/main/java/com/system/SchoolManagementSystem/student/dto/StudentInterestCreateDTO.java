package com.system.SchoolManagementSystem.student.dto;

import com.system.SchoolManagementSystem.student.entity.StudentInterest;
import lombok.Data;

@Data
public class StudentInterestCreateDTO {
    private StudentInterest.InterestType interestType;
    private String name;
    private String description;
}