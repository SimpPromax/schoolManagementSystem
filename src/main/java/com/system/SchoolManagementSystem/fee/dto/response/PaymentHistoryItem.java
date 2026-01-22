// PaymentHistoryItem.java
package com.system.SchoolManagementSystem.fee.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentHistoryItem {
    private Long id;
    private Double amount;
    private LocalDateTime date;
    private String method;
    private String receipt;
}