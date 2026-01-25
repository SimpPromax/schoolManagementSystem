package com.system.SchoolManagementSystem.termmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class TermFeeStatistics {

    private Long termId;
    private String termName;
    private String academicYear;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generatedAt;

    // Student statistics
    private Long totalStudents;
    private Long billedStudents;
    private Long unbilledStudents;

    // Financial statistics
    private Double totalExpectedFee;
    private Double totalCollected;
    private Double totalPending;
    private Double totalOverdue;
    private Double collectionRate;
    private Double averageFeePerStudent;

    // Status distribution
    private Map<String, Long> feeStatusDistribution; // PAID, PARTIAL, PENDING, OVERDUE

    // Grade-wise statistics
    private Map<String, GradeStatistics> gradeStatistics;

    // Fee type breakdown
    private Map<String, Double> feeTypeDistribution;

    // Collection trend (last 7 days)
    private Map<String, DailyCollection> dailyCollectionTrend;

    // Payment method distribution
    private Map<String, Double> paymentMethodDistribution;

    // Top performers and defaulters
    private TopPerformers topPerformers;
    private TopDefaulters topDefaulters;

    // Historical comparison
    private ComparisonData comparisonWithPreviousTerm;

    @Data
    public static class GradeStatistics {
        private String grade;
        private Long studentCount;
        private Double expectedFee;
        private Double collected;
        private Double pending;
        private Double collectionRate;
        private Double averageFee;
    }

    @Data
    public static class DailyCollection {
        private String date;
        private Double amount;
        private Integer transactionCount;
    }

    @Data
    public static class TopPerformers {
        private List<StudentPerformance> bestCollectors;
        private List<StudentPerformance> earlyPayers;
        private List<GradePerformance> bestPerformingGrades;
    }

    @Data
    public static class TopDefaulters {
        private List<StudentDefault> highestDefaulters;
        private List<GradeDefault> worstPerformingGrades;
        private List<LongTermDefaulters> longTermDefaulters;
    }

    @Data
    public static class StudentPerformance {
        private Long studentId;
        private String studentName;
        private String grade;
        private Double amountPaid;
        private Integer daysEarly;
        private String paymentMethod;
    }

    @Data
    public static class GradePerformance {
        private String grade;
        private Double collectionRate;
        private Double totalCollected;
        private Integer studentsPaid;
    }

    @Data
    public static class StudentDefault {
        private Long studentId;
        private String studentName;
        private String grade;
        private Double pendingAmount;
        private Integer daysOverdue;
        private Integer remindersSent;
    }

    @Data
    public static class GradeDefault {
        private String grade;
        private Double pendingAmount;
        private Integer defaultingStudents;
        private Double collectionRate;
    }

    @Data
    public static class LongTermDefaulters {
        private Long studentId;
        private String studentName;
        private String grade;
        private Integer termsWithOverdue;
        private Double totalHistoricalOverdue;
        private String parentContact;
    }

    @Data
    public static class ComparisonData {
        private Double previousTermCollection;
        private Double growthPercentage;
        private Integer previousTermStudents;
        private Integer studentGrowth;
        private Double previousTermAverage;
        private Double averageGrowth;
    }
}