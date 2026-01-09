package com.system.SchoolManagementSystem.transaction.dto.request;

import com.system.SchoolManagementSystem.transaction.enums.PaymentMethod;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PaymentVerificationRequest {
    private Long bankTransactionId;
    private Long studentId;
    private Long feeAssignmentId;
    private Long installmentId;
    private Double amount;
    private PaymentMethod paymentMethod;
    private LocalDate paymentDate;
    private String receiptNumber;
    private String notes;
    private Boolean sendSms = true;
    private String paymentFor;
    private Double discountApplied = 0.0;
    private Double lateFeePaid = 0.0;
    private Double convenienceFee = 0.0;
}