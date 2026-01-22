package com.system.SchoolManagementSystem.fee.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_collection_targets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FeeCollectionTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "target_name", nullable = false, length = 100)
    private String targetName;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType; // MONTHLY, QUARTERLY, YEARLY, CUSTOM

    @Column(name = "target_amount", nullable = false)
    private Double targetAmount;

    @Column(name = "collected_amount")
    @Builder.Default
    private Double collectedAmount = 0.0;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "grade_filter")
    private String gradeFilter; // Specific grade or "ALL"

    @Column(name = "percentage_completed")
    private Double percentageCompleted;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, COMPLETED, CANCELLED

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        if (targetAmount > 0) {
            this.percentageCompleted = (collectedAmount / targetAmount) * 100;
        }
    }
}