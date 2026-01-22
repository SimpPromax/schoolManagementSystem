package com.system.SchoolManagementSystem.fee.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class StudentPaymentHistoryResponse {
    private Long studentId;
    private String studentName;
    private String studentGrade;

    private Double totalFee;
    private Double totalPaid;
    private Double totalPending;
    private String paymentProgress;

    private List<PaymentTransactionDetail> transactions;
    private PaymentSummary summary;

    @Data
    public static class PaymentTransactionDetail {
        private Long id;
        private String receiptNumber;
        private Double amount;
        private String paymentMethod;
        private String paymentMethodDisplay;
        private LocalDateTime paymentDate;
        private String verifiedBy;
        private Integer installmentNumber;
        private String notes;
        private List<FeeBreakdown> breakdown;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeeBreakdown {
        private String category;
        private Double amount;
        private Double percentage;
    }

    @Data
    public static class PaymentSummary {
        private Integer totalTransactions;
        private LocalDateTime firstPayment;
        private LocalDateTime lastPayment;
        private List<String> paymentMethods;
        private Double averagePaymentAmount;
        private Long daysSinceFirstPayment;
    }
}