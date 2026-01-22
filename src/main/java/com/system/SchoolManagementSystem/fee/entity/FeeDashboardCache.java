package com.system.SchoolManagementSystem.fee.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_dashboard_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FeeDashboardCache {

    @Id
    @Column(name = "cache_key", length = 100)
    private String cacheKey;  // This is the primary key

    @Column(name = "cache_type", nullable = false, length = 50)
    private String cacheType; // STATS, TREND, PAYMENT_METHODS, OVERDUE

    @Lob
    @Column(name = "cache_data", nullable = false, columnDefinition = "LONGTEXT")
    private String cacheData; // JSON data

    @Column(name = "data_date", nullable = false)
    private LocalDate dataDate;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}