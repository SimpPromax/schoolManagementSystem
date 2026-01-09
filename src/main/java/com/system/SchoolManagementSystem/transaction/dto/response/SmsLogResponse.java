package com.system.SchoolManagementSystem.transaction.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SmsLogResponse {
    private Long id;
    private Long studentId;
    private String studentName;
    private Long paymentTransactionId;
    private String receiptNumber;
    private String recipientPhone;
    private String message;
    private String status;
    private String gatewayMessageId;
    private String deliveryStatus;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
}