package com.system.SchoolManagementSystem.termmanagement.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class BulkFeeResult {
    private int totalStudents;
    private int successCount;
    private int failedCount;
    private List<String> errors;
    private List<String> successes;
    private double totalAmount;
    private LocalDateTime timestamp;

    public BulkFeeResult() {
        this.timestamp = LocalDateTime.now();
        this.errors = new ArrayList<>();
        this.successes = new ArrayList<>();
    }

    public BulkFeeResult(int totalStudents, int successCount, int failedCount,
                         List<String> errors, List<String> successes, double totalAmount,
                         LocalDateTime timestamp) {
        this.totalStudents = totalStudents;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.errors = errors != null ? errors : new ArrayList<>();
        this.successes = successes != null ? successes : new ArrayList<>();
        this.totalAmount = totalAmount;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }
}