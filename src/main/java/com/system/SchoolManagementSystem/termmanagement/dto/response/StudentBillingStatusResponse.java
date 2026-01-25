package com.system.SchoolManagementSystem.termmanagement.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class StudentBillingStatusResponse {
    private Long studentId;
    private String studentCode;
    private String studentName;
    private String grade;
    private String className;
    private Double totalFee;
    private Double paidAmount;
    private Double pendingAmount;
    private String feeStatus;
    private Double paymentPercentage;
    private Boolean hasOverdue;
    private Double overdueAmount;
    private LocalDate lastPaymentDate;

    // Current term info
    private String currentTerm;
    private Long currentTermId;
    private Double currentTermFee;
    private Double currentTermPaid;
    private Double currentTermPending;
    private String currentTermStatus;
}
