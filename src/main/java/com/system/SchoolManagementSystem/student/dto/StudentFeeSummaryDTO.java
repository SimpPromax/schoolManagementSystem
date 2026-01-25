package com.system.SchoolManagementSystem.student.dto;

import com.system.SchoolManagementSystem.student.entity.Student;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
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

    // Constructor for JPQL query
    public StudentFeeSummaryDTO(
            Long studentId,
            String studentName,
            String grade,
            Double totalFee,
            Double paidAmount,
            Double pendingAmount,
            Student.FeeStatus feeStatus,
            Long transactionCount,
            LocalDateTime lastPaymentDate) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.grade = grade;
        this.totalFee = totalFee != null ? totalFee : 0.0;
        this.paidAmount = paidAmount != null ? paidAmount : 0.0;
        this.pendingAmount = pendingAmount != null ? pendingAmount : 0.0;
        this.feeStatus = feeStatus != null ? feeStatus : Student.FeeStatus.PENDING;
        this.transactionCount = transactionCount != null ? transactionCount.intValue() : 0;
        this.lastPaymentDate = lastPaymentDate;
    }
}