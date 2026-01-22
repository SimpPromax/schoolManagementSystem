package com.system.SchoolManagementSystem.fee.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class PaymentMethodDistributionResponse {
    private String period;
    private Long totalTransactions;
    private Double totalAmount;

    private List<PaymentMethodData> paymentMethods;

    @Data
    public static class PaymentMethodData {
        private String method;
        private String displayName;
        private Long transactionCount;
        private Double totalAmount;
        private Double percentage;
        private String color;
    }
}