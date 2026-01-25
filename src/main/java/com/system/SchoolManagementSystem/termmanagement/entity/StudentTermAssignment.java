package com.system.SchoolManagementSystem.termmanagement.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.transaction.entity.StudentFeeAssignment;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "student_term_assignments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "academic_term_id"}),
        indexes = {
                @Index(name = "idx_student_term", columnList = "student_id, academic_term_id"),
                @Index(name = "idx_term_status", columnList = "academic_term_id, term_fee_status"),
                @Index(name = "idx_due_date", columnList = "due_date"),
                @Index(name = "idx_fee_assignment", columnList = "student_fee_assignment_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"student", "academicTerm", "studentFeeAssignment", "feeItems"})
public class StudentTermAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonBackReference
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_term_id", nullable = false)
    @JsonBackReference
    private AcademicTerm academicTerm;

    @Column(name = "total_term_fee")
    private Double totalTermFee = 0.0;

    @Column(name = "paid_amount")
    private Double paidAmount = 0.0;

    @Column(name = "pending_amount")
    private Double pendingAmount = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "term_fee_status", nullable = false)
    private FeeStatus termFeeStatus = FeeStatus.PENDING;

    @Column(name = "is_billed", nullable = false)
    private Boolean isBilled = false;

    @Column(name = "billing_date")
    private LocalDate billingDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;

    @Column(name = "reminders_sent")
    private Integer remindersSent = 0;

    @Column(name = "last_reminder_date")
    private LocalDate lastReminderDate;

    // Link to existing StudentFeeAssignment
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_fee_assignment_id")
    private StudentFeeAssignment studentFeeAssignment;

    // Detailed fee items for this term
    @OneToMany(mappedBy = "studentTermAssignment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    @Builder.Default
    private List<TermFeeItem> feeItems = new ArrayList<>();

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        calculateAmounts();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        calculateAmounts();
    }

    public void calculateAmounts() {
        // Calculate from fee items
        double total = feeItems.stream()
                .mapToDouble(TermFeeItem::getAmount)
                .sum();

        double paid = feeItems.stream()
                .mapToDouble(TermFeeItem::getPaidAmount)
                .sum();

        this.totalTermFee = total;
        this.paidAmount = paid;
        this.pendingAmount = Math.max(0, total - paid);

        // Update status
        updateStatus();
    }

    private void updateStatus() {
        if (paidAmount >= totalTermFee) {
            this.termFeeStatus = FeeStatus.PAID;
        } else if (paidAmount > 0) {
            this.termFeeStatus = FeeStatus.PARTIAL;
        } else if (dueDate != null && LocalDate.now().isAfter(dueDate)) {
            this.termFeeStatus = FeeStatus.OVERDUE;
        } else {
            this.termFeeStatus = FeeStatus.PENDING;
        }
    }

    public void addFeeItem(TermFeeItem item) {
        feeItems.add(item);
        item.setStudentTermAssignment(this);
        calculateAmounts();
    }

    public void removeFeeItem(TermFeeItem item) {
        feeItems.remove(item);
        item.setStudentTermAssignment(null);
        calculateAmounts();
    }

    public enum FeeStatus {
        PENDING, PARTIAL, PAID, OVERDUE, CANCELLED, WAIVED
    }
}