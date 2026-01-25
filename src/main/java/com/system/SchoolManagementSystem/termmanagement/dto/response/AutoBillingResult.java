package com.system.SchoolManagementSystem.termmanagement.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class AutoBillingResult {
    private boolean success;
    private String message;
    private int billedCount;
    private int skippedCount;
    private List<String> errors;
    private List<String> successfulBills;
    private String termName;
    private String academicYear;
    private LocalDateTime timestamp;

    public AutoBillingResult() {
        this.timestamp = LocalDateTime.now();
        this.errors = new ArrayList<>();
        this.successfulBills = new ArrayList<>();
    }

    public AutoBillingResult(boolean success, String message, int billedCount, int skippedCount,
                             List<String> errors, List<String> successfulBills, String termName,
                             String academicYear, LocalDateTime timestamp) {
        this.success = success;
        this.message = message;
        this.billedCount = billedCount;
        this.skippedCount = skippedCount;
        this.errors = errors != null ? errors : new ArrayList<>();
        this.successfulBills = successfulBills != null ? successfulBills : new ArrayList<>();
        this.termName = termName;
        this.academicYear = academicYear;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }
}