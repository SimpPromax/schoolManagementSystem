package com.system.SchoolManagementSystem.termmanagement.dto.response;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
public class SchoolFeeSummary {
    private String currentTerm;
    private String academicYear;
    private Long currentTermId;
    private Long totalStudents;
    private Long activeStudents;
    private Long paidStudents;
    private Long pendingStudents;
    private Long overdueStudents;
    private Double totalExpectedFee;
    private Double totalCollected;
    private Double totalPending;
    private Double collectionRate;
    private Map<String, GradeSummary> gradeSummaries;
    private LocalDateTime timestamp;

    public SchoolFeeSummary() {
        this.timestamp = LocalDateTime.now();
        this.gradeSummaries = new HashMap<>();
        // Initialize all fields to avoid null
        this.totalStudents = 0L;
        this.activeStudents = 0L;
        this.paidStudents = 0L;
        this.pendingStudents = 0L;
        this.overdueStudents = 0L;
        this.totalExpectedFee = 0.0;
        this.totalCollected = 0.0;
        this.totalPending = 0.0;
        this.collectionRate = 0.0;
    }

    @Data
    public static class GradeSummary {
        private Long totalStudents;
        private Long paidStudents;
        private Long pendingStudents;
        private Long overdueStudents;
        private Double totalFee;
        private Double totalCollected;
        private Double totalPending;
        private Double collectionRate;

        public GradeSummary() {
            this.totalStudents = 0L;
            this.paidStudents = 0L;
            this.pendingStudents = 0L;
            this.overdueStudents = 0L;
            this.totalFee = 0.0;
            this.totalCollected = 0.0;
            this.totalPending = 0.0;
            this.collectionRate = 0.0;
        }
    }
}