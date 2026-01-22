package com.system.SchoolManagementSystem.fee.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CollectionTrendResponse {
    private String period; // DAILY, WEEKLY, MONTHLY, QUARTERLY
    private LocalDate startDate;
    private LocalDate endDate;

    private List<TrendDataPoint> dataPoints;

    @Data
    public static class TrendDataPoint {
        private LocalDate date;
        private Double collectedAmount;
        private Double targetAmount;
        private Double overdueAmount;
        private Long transactionCount;
        private Long studentCount;
    }
}