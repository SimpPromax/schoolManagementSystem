package com.system.SchoolManagementSystem.termmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BulkUpdateResult {

    private int totalStudents;
    private int successCount;
    private int failedCount;
    private int skippedCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    private List<String> errors;
    private List<String> successes;
    private List<String> skipped;

    private String operationType;
    private String performedBy;
    private String summaryMessage;

    // Detailed statistics
    private List<StudentUpdateDetail> studentDetails;

    @Data
    public static class StudentUpdateDetail {
        private Long studentId;
        private String studentName;
        private String status; // SUCCESS, FAILED, SKIPPED
        private String message;
        private String previousStatus;
        private String newStatus;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updatedAt;
    }
}