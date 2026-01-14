package com.system.SchoolManagementSystem.transaction.dto.response;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.transaction.enums.PaymentMethod;
import com.system.SchoolManagementSystem.transaction.enums.TransactionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BankTransactionResponse {
    private Long id;
    private String bankReference;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate transactionDate;

    private String description;
    private Double amount;
    private String bankAccount;
    private TransactionStatus status;
    private PaymentMethod paymentMethod;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime importedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime matchedAt;

    private String fileName;
    private String importBatchId;

    // Student information
    private Long studentId;
    private String studentName;
    private String studentGrade;

    // NEW: Student fee information
    private Double studentPendingAmount;
    private Student.FeeStatus studentFeeStatus; // Import the FeeStatus enum from Student

    // Optional: Additional fee details
    private Double studentTotalFee;
    private Double studentPaidAmount;
    private Double studentPaymentPercentage;

    // Getters and setters (Lombok @Data will generate them, but we can add custom ones)

    public void setStudentFeeStatus(Student.FeeStatus feeStatus) {
        this.studentFeeStatus = feeStatus;
    }

    // If you need to set fee status from string
    public void setStudentFeeStatus(String feeStatus) {
        if (feeStatus != null) {
            try {
                this.studentFeeStatus = Student.FeeStatus.valueOf(feeStatus);
            } catch (IllegalArgumentException e) {
                this.studentFeeStatus = null;
            }
        }
    }
}