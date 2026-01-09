package com.system.SchoolManagementSystem.student.dto;

import com.system.SchoolManagementSystem.student.entity.Student;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StudentFeeSummaryDTO {
    private Long studentId;
    private String studentName;
    private String grade;
    private Double totalFee;
    private Double paidAmount;
    private Double pendingAmount;
    private Student.FeeStatus feeStatus;
    private Integer transactionCount;
    private LocalDateTime lastPaymentDate;
}