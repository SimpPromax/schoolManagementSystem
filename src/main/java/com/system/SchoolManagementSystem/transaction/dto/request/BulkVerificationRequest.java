package com.system.SchoolManagementSystem.transaction.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class BulkVerificationRequest {
    private List<Long> bankTransactionIds;
    private Boolean sendSms = true;
    private String notes;
}