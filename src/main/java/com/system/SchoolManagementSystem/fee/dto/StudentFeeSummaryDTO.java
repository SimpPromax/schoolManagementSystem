// StudentFeeSummaryDTO.java
package com.system.SchoolManagementSystem.fee.dto;

import com.system.SchoolManagementSystem.student.entity.Student;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class StudentFeeSummaryDTO {
    // Basic student info
    private Long studentId;
    private String studentName;
    private String grade;
    private String rollNumber;

    // Guardian info (from FamilyMember)
    private String guardianName;
    private String guardianContact;
    private String guardianEmail;
    private String guardianRelationship;

    // Contact info (from Student)
    private String studentPhone;
    private String studentEmail;

    // Fee info (from Student entity)
    private Double totalFee;
    private Double paidAmount;
    private Double pendingAmount;
    private Student.FeeStatus feeStatus;

    // Dynamic/computed fields
    private LocalDate dueDate;              // From fee assignment
    private LocalDate lastPaymentDate;      // From transactions
    private LocalDate lastReminderDate;     // From reminders
    private Integer remindersSent;          // From reminders
    private Integer paymentCount;           // From transactions
    private Double paymentPercentage;       // Computed

    // Recent transactions (last 3)
    private List<PaymentSummary> recentPayments;

    @Data
    public static class PaymentSummary {
        private Long transactionId;
        private Double amount;
        private LocalDateTime paymentDate;
        private String paymentMethod;
        private String receiptNumber;
        private String notes;
    }
}