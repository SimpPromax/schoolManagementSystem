package com.system.SchoolManagementSystem.termmanagement.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "grade_term_fees",
        uniqueConstraints = @UniqueConstraint(columnNames = {"academic_term_id", "grade"}),
        indexes = {
                @Index(name = "idx_grade_term", columnList = "grade, academic_term_id"),
                @Index(name = "idx_term_active", columnList = "academic_term_id, is_active")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "academicTerm")
public class GradeTermFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_term_id", nullable = false)
    @JsonBackReference
    private AcademicTerm academicTerm;

    @Column(nullable = false, length = 20)
    private String grade; // e.g., "Grade 1", "Grade 2"

    // Basic Fees
    @Column(name = "tuition_fee", nullable = false)
    private Double tuitionFee = 0.0;

    @Column(name = "basic_fee", nullable = false)
    private Double basicFee = 0.0;

    @Column(name = "examination_fee")
    private Double examinationFee = 0.0;

    // Optional Fees
    @Column(name = "transport_fee")
    private Double transportFee = 0.0;

    @Column(name = "library_fee")
    private Double libraryFee = 0.0;

    @Column(name = "sports_fee")
    private Double sportsFee = 0.0;

    @Column(name = "activity_fee")
    private Double activityFee = 0.0;

    @Column(name = "hostel_fee")
    private Double hostelFee = 0.0;

    @Column(name = "uniform_fee")
    private Double uniformFee = 0.0;

    @Column(name = "book_fee")
    private Double bookFee = 0.0;

    @Column(name = "other_fees")
    private Double otherFees = 0.0;

    @Column(name = "total_fee", nullable = false)
    private Double totalFee = 0.0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        calculateTotalFee();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        calculateTotalFee();
    }

    private void calculateTotalFee() {
        double total = 0.0;
        total += tuitionFee != null ? tuitionFee : 0.0;
        total += basicFee != null ? basicFee : 0.0;
        total += examinationFee != null ? examinationFee : 0.0;
        total += transportFee != null ? transportFee : 0.0;
        total += libraryFee != null ? libraryFee : 0.0;
        total += sportsFee != null ? sportsFee : 0.0;
        total += activityFee != null ? activityFee : 0.0;
        total += hostelFee != null ? hostelFee : 0.0;
        total += uniformFee != null ? uniformFee : 0.0;
        total += bookFee != null ? bookFee : 0.0;
        total += otherFees != null ? otherFees : 0.0;
        this.totalFee = total;
    }

    public Double getFeeForType(String feeType) {
        switch (feeType.toUpperCase()) {
            case "TUITION": return tuitionFee;
            case "BASIC": return basicFee;
            case "EXAMINATION": return examinationFee;
            case "TRANSPORT": return transportFee;
            case "LIBRARY": return libraryFee;
            case "SPORTS": return sportsFee;
            case "ACTIVITY": return activityFee;
            case "HOSTEL": return hostelFee;
            case "UNIFORM": return uniformFee;
            case "BOOK": return bookFee;
            case "OTHER": return otherFees;
            default: return 0.0;
        }
    }
}