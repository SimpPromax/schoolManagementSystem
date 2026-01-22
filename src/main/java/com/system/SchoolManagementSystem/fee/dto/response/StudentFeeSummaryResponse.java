package com.system.SchoolManagementSystem.fee.dto.response;

import com.system.SchoolManagementSystem.student.entity.Student;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class StudentFeeSummaryResponse {
    private Long studentId;
    private String studentName;
    private String studentIdNumber;
    private String grade;
    private String className;
    private String guardianName;
    private String contact;
    private String email;

    private Double totalFee;
    private Double paidAmount;
    private Double pendingAmount;
    private Double paymentPercentage;

    private Student.FeeStatus feeStatus;
    private LocalDate dueDate;
    private LocalDate lastPaymentDate;

    private Integer remindersSent;
    private LocalDate lastReminderDate;

    private Integer paymentCount;
    private List<String> paymentMethodsUsed;

    private Boolean hasMultiplePayments;
    private Double averagePaymentAmount;
}