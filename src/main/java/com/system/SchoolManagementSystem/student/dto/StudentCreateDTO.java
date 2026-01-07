package com.system.SchoolManagementSystem.student.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.system.SchoolManagementSystem.student.entity.Student;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StudentCreateDTO {
    private String studentId;
    private String fullName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    private Student.Gender gender;
    private Student.BloodGroup bloodGroup;
    private String nationality;
    private String religion;
    private Student.Category category;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate admissionDate;

    private String academicYear;
    private String grade;
    private String rollNumber;
    private String classTeacher;
    private String house;

    // Contact
    private String address;
    private String phone;
    private String email;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String emergencyRelation;
}