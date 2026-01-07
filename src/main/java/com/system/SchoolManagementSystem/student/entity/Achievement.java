package com.system.SchoolManagementSystem.student.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "achievements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Achievement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnore
    private Student student;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AchievementType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AchievementLevel level;

    @Column(nullable = false)
    private Integer year;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    private String award;

    @Column(name = "certificate_path")
    private String certificatePath;

    @Column(name = "verified_by")
    private String verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum AchievementType {
        ACADEMIC, SPORTS, ARTS, CULTURAL, LEADERSHIP, OTHER
    }

    public enum AchievementLevel {
        SCHOOL, DISTRICT, STATE, NATIONAL, INTERNATIONAL
    }
}