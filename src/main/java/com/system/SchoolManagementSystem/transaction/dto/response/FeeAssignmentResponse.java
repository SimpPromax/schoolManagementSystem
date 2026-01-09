package com.system.SchoolManagementSystem.transaction.dto.response;

import com.system.SchoolManagementSystem.transaction.enums.FeeStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class FeeAssignmentResponse {
    private Long id;
    private Long studentId;
    private String studentName;
    private String studentGrade;
    private Long feeStructureId;
    private String feeStructureName;
    private String academicYear;
    private LocalDate assignedDate;
    private Double totalAmount;
    private Double paidAmount;
    private Double pendingAmount;
    private FeeStatus feeStatus;
    private LocalDate lastPaymentDate;
    private LocalDate dueDate;
    private Integer remindersSent;
    private LocalDate lastReminderDate;
    private Boolean isActive;
    private List<InstallmentResponse> installments;
    private LocalDateTime createdAt;

    @Data
    public static class InstallmentResponse {
        private Long id;
        private Integer installmentNumber;
        private String installmentName;
        private Double amount;
        private Double paidAmount;
        private LocalDate dueDate;
        private FeeStatus status;
        private Double lateFeeCharged;
        private Double discountAmount;
        private Double netAmount;
        private LocalDate paymentDeadline;
    }
}