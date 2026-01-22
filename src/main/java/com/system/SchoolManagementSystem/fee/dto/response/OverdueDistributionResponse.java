package com.system.SchoolManagementSystem.fee.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class OverdueDistributionResponse {
    private Long totalOverdueStudents;
    private Double totalOverdueAmount;

    private List<OverdueRange> overdueRanges;

    @Data
    public static class OverdueRange {
        private String range; // "1-7 days", "8-15 days", "16-30 days", "30+ days"
        private Long studentCount;
        private Double totalAmount;
        private Double percentage;
    }
}