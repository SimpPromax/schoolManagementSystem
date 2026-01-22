package com.system.SchoolManagementSystem.fee.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class BulkReminderResultResponse {
    private Integer totalSelected;
    private Integer successfullySent;
    private Integer failed;
    private Double totalPendingAmount;

    private List<ReminderResult> results;

    @Data
    public static class ReminderResult {
        private Long studentId;
        private String studentName;
        private String channel;
        private String status;
        private String message;
        private String error;
    }
}