package com.system.SchoolManagementSystem.transaction.entity;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.transaction.enums.PaymentMethod;
import com.system.SchoolManagementSystem.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "receipt_number", unique = true, length = 50)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_assignment_id")
    private StudentFeeAssignment feeAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installment_id")
    private FeeInstallment installment;

    @Column(nullable = false)
    private Double amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private LocalDateTime paymentDate;

    // ========== UPDATED: Bank transaction is now REQUIRED ==========
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_transaction_id", nullable = false)
    private BankTransaction bankTransaction;

    @Column(name = "bank_reference", length = 50)
    private String bankReference;

    @Builder.Default
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Builder.Default
    @Column(name = "sms_sent", nullable = false)
    private Boolean smsSent = false;

    @Column(name = "sms_sent_at")
    private LocalDateTime smsSentAt;

    @Column(name = "sms_id")
    private String smsId;

    @Column(name = "notes", length = 1000)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "payment_for", length = 100)
    private String paymentFor;

    @Builder.Default
    @Column(name = "discount_applied")
    private Double discountApplied = 0.0;

    @Builder.Default
    @Column(name = "late_fee_paid")
    private Double lateFeePaid = 0.0;

    @Builder.Default
    @Column(name = "convenience_fee")
    private Double convenienceFee = 0.0;

    @Column(name = "total_paid")
    private Double totalPaid;

    @PrePersist
    protected void onCreate() {
        if (receiptNumber == null) {
            receiptNumber = "RC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        // Automatically set bank reference from bank transaction
        if (bankTransaction != null && bankReference == null) {
            bankReference = bankTransaction.getBankReference();
        }
        calculateTotal();
    }

    @PreUpdate
    protected void onUpdate() {
        calculateTotal();
    }

    private void calculateTotal() {
        totalPaid = amount - discountApplied + lateFeePaid + convenienceFee;
    }
}