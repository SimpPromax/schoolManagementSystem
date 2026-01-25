package com.system.SchoolManagementSystem.termmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OverdueFeesReport {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generatedDate;

    private String reportPeriod;
    private Double totalOverdueAmount;
    private Integer totalStudents;
    private Integer totalOverdueItems;
    private Integer overdueThresholdDays = 30;

    private List<OverdueStudent> overdueItems;

    // Statistics
    private Double averageOverduePerStudent;
    private Integer studentsWithHighOverdue; // > 100,000
    private Integer studentsWithMediumOverdue; // 50,000 - 100,000
    private Integer studentsWithLowOverdue; // < 50,000

    // Grade-wise breakdown
    private List<GradeOverdueSummary> gradeBreakdown;

    // Term-wise breakdown
    private List<TermOverdueSummary> termBreakdown;

    @Data
    public static class OverdueStudent {
        private Long studentId;
        private String studentName;
        private String studentCode;
        private String grade;
        private String className;
        private Double totalOverdueAmount;
        private Integer overdueItemsCount;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate earliestDueDate;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate latestDueDate;

        private Integer daysOverdue;
        private String parentName;
        private String parentPhone;
        private String parentEmail;
        private Integer remindersSent;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate lastReminderDate;
    }

    @Data
    public static class GradeOverdueSummary {
        private String grade;
        private Integer studentCount;
        private Double totalOverdueAmount;
        private Double averageOverdue;
    }

    @Data
    public static class TermOverdueSummary {
        private String termName;
        private String academicYear;
        private Integer studentCount;
        private Double totalOverdueAmount;
    }
}