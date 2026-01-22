package com.system.SchoolManagementSystem.fee.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReportResponse {
    private String reportId;
    private String reportName;
    private String reportType;
    private String format;

    private String downloadUrl;
    private String filePath;
    private Long fileSize;

    private LocalDateTime generatedAt;
    private String generatedBy;

    private ReportMetadata metadata;

    @Data
    public static class ReportMetadata {
        private Integer studentCount;
        private Integer transactionCount;
        private Double totalAmount;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
        private List<String> includedGrades;
        private List<String> includedClasses;
    }
}