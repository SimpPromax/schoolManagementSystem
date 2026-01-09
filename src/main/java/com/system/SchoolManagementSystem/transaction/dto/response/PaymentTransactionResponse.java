package com.system.SchoolManagementSystem.transaction.dto.response;

import com.system.SchoolManagementSystem.transaction.enums.PaymentMethod;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentTransactionResponse {
    private Long id;
    private String receiptNumber;
    private Long studentId;
    private String studentName;
    private String studentGrade;
    private Long feeAssignmentId;
    private Long installmentId;
    private Double amount;
    private PaymentMethod paymentMethod;
    private LocalDateTime paymentDate;
    private Long bankTransactionId;
    private String bankReference;
    private Boolean isVerified;
    private String verifiedBy;
    private LocalDateTime verifiedAt;
    private Boolean smsSent;
    private LocalDateTime smsSentAt;
    private String notes;
    private String paymentFor;
    private Double discountApplied;
    private Double lateFeePaid;
    private Double convenienceFee;
    private Double totalPaid;
    private LocalDateTime createdAt;
}