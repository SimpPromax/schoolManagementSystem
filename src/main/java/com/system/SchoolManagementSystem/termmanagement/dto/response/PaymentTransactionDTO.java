package com.system.SchoolManagementSystem.termmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PaymentTransactionDTO {

    private Long id;
    private Long studentId;
    private String studentName;
    private String studentCode;
    private String grade;

    private String transactionCode;
    private Double amount;
    private String paymentMethod;
    private String referenceNumber;
    private String bankName;
    private String branchName;
    private String chequeNumber;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime paymentDate;

    private String collectedBy;
    private String status;
    private String receiptNumber;

    private String academicYear;
    private String termName;

    private String notes;
    private Boolean isReconciled = false;

    private Double appliedAmount;
    private Double remainingAmount;

    // For payment allocation
    private java.util.List<AppliedFeeItem> appliedFeeItems;

    @Data
    public static class AppliedFeeItem {
        private Long feeItemId;
        private String itemName;
        private String feeType;
        private Double amountApplied;
        private Double remainingBalance;
        private String newStatus;
    }
}