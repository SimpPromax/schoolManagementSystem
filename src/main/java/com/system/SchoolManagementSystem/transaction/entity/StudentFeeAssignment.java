package com.system.SchoolManagementSystem.transaction.entity;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.transaction.enums.FeeStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "student_fee_assignments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class StudentFeeAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_structure_id", nullable = false)
    private FeeStructure feeStructure;

    @Column(name = "academic_year", nullable = false, length = 10)
    private String academicYear;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate;

    @Column(name = "total_amount", nullable = false)
    private Double totalAmount;

    @Column(name = "paid_amount", nullable = false)
    @Builder.Default
    private Double paidAmount = 0.0;

    @Column(name = "pending_amount", nullable = false)
    private Double pendingAmount;

    // ========== ADD DISCOUNT FIELD ==========
    @Column(name = "discount_amount")
    @Builder.Default
    private Double discountAmount = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_status", nullable = false)
    @Builder.Default
    private FeeStatus feeStatus = FeeStatus.PENDING;

    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "reminders_sent")
    @Builder.Default
    private Integer remindersSent = 0;

    @Column(name = "last_reminder_date")
    private LocalDate lastReminderDate;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "feeAssignment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FeeInstallment> installments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatePendingAmount();
        calculateNetAmount();
    }

    @PreUpdate
    protected void onUpdate() {
        updatePendingAmount();
        updateFeeStatus();
        calculateNetAmount();
    }

    private void updatePendingAmount() {
        pendingAmount = (totalAmount - discountAmount) - paidAmount;
        if (pendingAmount < 0) {
            pendingAmount = 0.0;
        }
    }

    private void updateFeeStatus() {
        double netAmount = totalAmount - discountAmount;

        if (paidAmount >= netAmount) {
            feeStatus = FeeStatus.PAID;
        } else if (dueDate != null && LocalDate.now().isAfter(dueDate)) {
            feeStatus = FeeStatus.OVERDUE;
        } else if (paidAmount > 0) {
            feeStatus = FeeStatus.PARTIAL;
        } else {
            feeStatus = FeeStatus.PENDING;
        }
    }

    private void calculateNetAmount() {
        // This method ensures discount is applied correctly
        // Net amount = totalAmount - discountAmount
    }

    // ========== HELPER METHODS ==========

    /**
     * Get net amount after discount
     */
    public Double getNetAmount() {
        return totalAmount - discountAmount;
    }

    /**
     * Apply discount to the assignment
     */
    public void applyDiscount(Double discount) {
        if (discount == null || discount < 0) {
            throw new IllegalArgumentException("Discount cannot be negative");
        }
        if (discount > totalAmount) {
            throw new IllegalArgumentException("Discount cannot exceed total amount");
        }
        this.discountAmount = discount;
        updatePendingAmount();
        updateFeeStatus();
    }

    /**
     * Remove discount
     */
    public void removeDiscount() {
        this.discountAmount = 0.0;
        updatePendingAmount();
        updateFeeStatus();
    }

    /**
     * Get discount percentage
     */
    public Double getDiscountPercentage() {
        if (totalAmount == null || totalAmount == 0) {
            return 0.0;
        }
        return (discountAmount / totalAmount) * 100;
    }
}