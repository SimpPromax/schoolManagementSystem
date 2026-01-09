package com.system.SchoolManagementSystem.transaction.dto.request;

import lombok.Data;

@Data
public class SmsRequest {
    private Long studentId;
    private Long paymentTransactionId;
    private String message;
    private String recipientPhone;
}