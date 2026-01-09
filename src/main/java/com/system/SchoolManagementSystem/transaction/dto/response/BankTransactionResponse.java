package com.system.SchoolManagementSystem.transaction.dto.response;

import com.system.SchoolManagementSystem.transaction.enums.PaymentMethod;
import com.system.SchoolManagementSystem.transaction.enums.TransactionStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BankTransactionResponse {
    private Long id;
    private String bankReference;
    private LocalDate transactionDate;
    private String description;
    private Double amount;
    private String bankAccount;
    private TransactionStatus status;
    private PaymentMethod paymentMethod;
    private Long studentId;
    private String studentName;
    private String studentGrade;
    private LocalDateTime importedAt;
    private LocalDateTime matchedAt;
    private String fileName;
    private String importBatchId;
}