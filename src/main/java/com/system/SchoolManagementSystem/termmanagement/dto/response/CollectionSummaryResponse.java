package com.system.SchoolManagementSystem.termmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class CollectionSummaryResponse {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generatedAt;

    // Overall statistics
    private Double totalCollections;
    private Integer transactionsCount;
    private Double averageTransactionAmount;
    private Double highestTransactionAmount;
    private Double lowestTransactionAmount;

    // Daily breakdown
    private Map<LocalDate, DailyCollection> dailyCollections;

    // Payment method breakdown
    private Map<String, PaymentMethodSummary> paymentMethodBreakdown;

    // Grade-wise breakdown
    private Map<String, GradeCollectionSummary> gradeBreakdown;

    // Collector breakdown
    private Map<String, CollectorSummary> collectorBreakdown;

    // Term-wise breakdown
    private Map<String, TermCollectionSummary> termBreakdown;

    // Trend analysis
    private Double collectionGrowthRate; // Compared to previous period
    private Integer newPayersCount;
    private Integer repeatPayersCount;

    @Data
    public static class DailyCollection {
        private LocalDate date;
        private Double amount;
        private Integer transactionCount;
        private Integer studentCount;
    }

    @Data
    public static class PaymentMethodSummary {
        private String method;
        private Double amount;
        private Integer transactionCount;
        private Double percentage;
    }

    @Data
    public static class GradeCollectionSummary {
        private String grade;
        private Double amount;
        private Integer studentCount;
        private Integer transactionCount;
        private Double averagePerStudent;
    }

    @Data
    public static class CollectorSummary {
        private String collectorName;
        private Double amount;
        private Integer transactionCount;
        private Integer studentCount;
    }

    @Data
    public static class TermCollectionSummary {
        private String termName;
        private String academicYear;
        private Double amount;
        private Integer studentCount;
        private Double collectionRate; // Percentage of expected collection
    }
}