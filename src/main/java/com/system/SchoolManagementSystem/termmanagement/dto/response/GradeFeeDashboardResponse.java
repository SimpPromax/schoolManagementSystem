package com.system.SchoolManagementSystem.termmanagement.dto.response;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class GradeFeeDashboardResponse {
    private String grade;
    private String termName;
    private String academicYear;

    // Statistics
    private Integer studentsEnrolled;
    private Integer billedStudents;
    private Double expectedRevenue;
    private Double collected;
    private Double outstanding;
    private Double collectionRate;

    // Payment Status Breakdown
    private Map<String, Integer> paymentStatus;

    // Fee Type Breakdown
    private Map<String, Double> feeBreakdown;

    // Collection Trend (last 4 weeks)
    private List<WeeklyCollection> collectionTrend;

    // Top Defaulters
    private List<TopDefaulter> topDefaulters;

    // Fee Structure
    private FeeStructureResponse feeStructure;

    @Data
    public static class WeeklyCollection {
        private String week;
        private Double amount;
    }

    @Data
    public static class TopDefaulter {
        private Long studentId;
        private String studentName;
        private String studentCode;
        private Double pendingAmount;
    }

    @Data
    public static class FeeStructureResponse {
        private Double tuitionFee;
        private Double basicFee;
        private Double examinationFee;
        private Double transportFee;
        private Double libraryFee;
        private Double sportsFee;
        private Double activityFee;
        private Double hostelFee;
        private Double uniformFee;
        private Double bookFee;
        private Double otherFees;
        private Double totalFee;
    }
}