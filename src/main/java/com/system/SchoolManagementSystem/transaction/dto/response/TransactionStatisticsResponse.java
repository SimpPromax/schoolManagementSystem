package com.system.SchoolManagementSystem.transaction.dto.response;

import lombok.Data;
import java.util.Map;

@Data
public class TransactionStatisticsResponse {
    private Long unverifiedCount;
    private Long matchedCount;
    private Long verifiedCount;
    private Double totalAmount;
    private Double todayAmount;
    private String matchRate;
    private Long pendingPayments;
    private Long overduePayments;
    private Double totalPendingAmount;
    // Add this setter method
    private Map<String, Object> feeStatistics; // Add this field for term fee stats

    public TransactionStatisticsResponse() {
        this.unverifiedCount = 0L;
        this.matchedCount = 0L;
        this.verifiedCount = 0L;
        this.totalAmount = 0.0;
        this.todayAmount = 0.0;
        this.matchRate = "0%";
        this.pendingPayments = 0L;
        this.overduePayments = 0L;
        this.totalPendingAmount = 0.0;
    }

}