package com.system.SchoolManagementSystem.transaction.dto.response;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.transaction.enums.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentTransactionResponse {
    private Long id;
    private String receiptNumber;
    private Double amount;
    private PaymentMethod paymentMethod;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime paymentDate;

    private Boolean isVerified;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime verifiedAt;

    private Boolean smsSent;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime smsSentAt;

    private String notes;
    private String paymentFor;
    private Double discountApplied;
    private Double lateFeePaid;
    private Double convenienceFee;
    private Double totalPaid;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Student information
    private Long studentId;
    private String studentName;
    private String studentGrade;

    // NEW: Student fee information
    private Double studentPendingAmount;
    private Student.FeeStatus studentFeeStatus; // Import the FeeStatus enum from Student

    // Bank transaction reference
    private Long bankTransactionId;
    private String bankReference;

    // Optional: Additional fee details
    private Double studentTotalFee;
    private Double studentPaidAmount;
    private Double studentPaymentPercentage;

    // Getters and setters

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