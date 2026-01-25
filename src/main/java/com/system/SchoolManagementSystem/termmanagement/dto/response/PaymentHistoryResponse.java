package com.system.SchoolManagementSystem.termmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class PaymentHistoryResponse {

    private Long studentId;
    private String studentName;
    private String studentCode;
    private String grade;
    private Integer totalPayments;
    private Double totalAmountPaid;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastPaymentDate;

    private List<PaymentDetail> paymentHistory;
    private Map<Long, String> termContext;

    @Data
    public static class PaymentDetail {
        private Long transactionId;
        private String transactionCode;
        private Double amount;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime paymentDate;

        private String paymentMethod;
        private String referenceNumber;
        private String collectedBy;
        private String status;
        private String receiptNumber;
        private String bankName;
        private String branchName;
        private String chequeNumber;

        private List<PaidFeeItem> paidItems;
    }

    @Data
    public static class PaidFeeItem {
        private Long itemId;
        private String itemName;
        private String feeType;
        private Double amountPaid;
        private String termName;
        private String academicYear;
    }
}