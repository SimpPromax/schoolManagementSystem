package com.system.SchoolManagementSystem.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    @Column(name = "database_name", nullable = false, unique = true, length = 50)
    private String databaseName;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "domain", unique = true, length = 255)
    private String domain;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "admin_username", length = 100)
    private String adminUsername;

    @Column(name = "admin_password", length = 255)
    private String adminPassword;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void validate() {
        if (databaseName != null) {
            databaseName = databaseName.toLowerCase();
        }
        if (domain != null) {
            domain = domain.toLowerCase();
        }
    }
}