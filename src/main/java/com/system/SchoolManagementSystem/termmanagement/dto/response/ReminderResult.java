package com.system.SchoolManagementSystem.termmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReminderResult {

    private int totalStudents;
    private int sentCount;
    private int failedCount;
    private int skippedCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    private List<String> sentReminders;
    private List<String> failedReminders;
    private List<String> skippedReminders;

    private String reminderType;
    private String senderName;

    // Cost analysis (for SMS/Email services)
    private Double estimatedCost;
    private Integer smsCount;
    private Integer emailCount;

    // Delivery statistics
    private List<DeliveryStatus> deliveryStatuses;

    // Response tracking
    private Integer responsesReceived;
    private List<ReminderResponse> responses;

    @Data
    public static class DeliveryStatus {
        private Long studentId;
        private String contactMethod; // SMS, EMAIL
        private String status; // SENT, DELIVERED, FAILED
        private String messageId;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime sentAt;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime deliveredAt;

        private String failureReason;
    }

    @Data
    public static class ReminderResponse {
        private Long studentId;
        private String responseType; // PAYMENT_MADE, ACKNOWLEDGED, QUERY
        private String responseMessage;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime respondedAt;

        private Double amountPaid;
        private String paymentReference;
    }
}