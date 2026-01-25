package com.system.SchoolManagementSystem.termmanagement.dto.response;

import com.system.SchoolManagementSystem.student.entity.Student;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class PaymentApplicationResponse {
    private Long studentId;
    private String studentName;
    private String grade;
    private Double totalPayment;
    private Double appliedPayment;
    private Double remainingPayment;
    private Boolean allPaid;
    private LocalDateTime paymentDate;
    private List<AppliedItem> appliedItems = new ArrayList<>();

    @Data
    public static class AppliedItem {
        private Long itemId;
        private String itemName;
        private String feeType;
        private Double amountApplied;
        private String newStatus;
        private Double remainingBalance;
    }

    // Static factory method
    public static PaymentApplicationResponse fromStudent(Student student, Double amount) {
        PaymentApplicationResponse response = new PaymentApplicationResponse();
        response.setStudentId(student.getId());
        response.setStudentName(student.getFullName());
        response.setGrade(student.getGrade());
        response.setTotalPayment(amount);
        response.setPaymentDate(LocalDateTime.now());
        return response;
    }

    public void addAppliedItem(AppliedItem item) {
        this.appliedItems.add(item);
    }

    public void calculateAppliedTotal() {
        this.appliedPayment = appliedItems.stream()
                .mapToDouble(AppliedItem::getAmountApplied)
                .sum();
    }
}