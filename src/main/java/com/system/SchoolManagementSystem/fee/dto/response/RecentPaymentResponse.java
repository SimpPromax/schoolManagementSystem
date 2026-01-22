package com.system.SchoolManagementSystem.fee.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RecentPaymentResponse {
    private Long id;
    private String receiptNumber;
    private Double amount;
    private String paymentMethod;
    private String paymentMethodDisplay;
    private LocalDateTime paymentDate;

    private Long studentId;
    private String studentName;
    private String studentGrade;
    private String studentClass;

    private Boolean verified;
    private String verifiedBy;
    private LocalDateTime verifiedAt;

    private Integer installmentNumber;
    private String notes;
    private String bankReference;
    private String paymentFor;
}