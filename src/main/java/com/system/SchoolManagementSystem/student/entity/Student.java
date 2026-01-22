package com.system.SchoolManagementSystem.student.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "students")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"familyMembers", "medicalRecords", "achievements", "interests"})
public class Student {
// leon uutatumia rentity graphs ukishaverify the repository sql statements for faster querries
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "student_id", unique = true, nullable = false, length = 20)
    @EqualsAndHashCode.Include
    private String studentId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group")
    private BloodGroup bloodGroup;

    private String nationality;
    private String religion;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(name = "profile_picture")
    private String profilePicture;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    @Column(name = "academic_year", nullable = false, length = 10)
    private String academicYear;

    // Academic details
    @Column(nullable = false, length = 20)
    private String grade;

    @Column(name = "roll_number", length = 20)
    private String rollNumber;

    @Column(name = "class_teacher", length = 100)
    private String classTeacher;

    private String house;

    // Contact details
    private String address;
    private String phone;
    private String email;

    @Column(name = "emergency_contact_name", length = 100)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "emergency_relation", length = 50)
    private String emergencyRelation;

    // Medical
    private String height;
    private String weight;

    @Column(name = "blood_pressure", length = 20)
    private String bloodPressure;

    @Column(name = "last_medical_checkup")
    private LocalDate lastMedicalCheckup;

    @Column(name = "doctor_name", length = 100)
    private String doctorName;

    @Column(name = "clinic_name", length = 100)
    private String clinicName;

    // Transport
    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode")
    private TransportMode transportMode;

    @Column(name = "bus_route", length = 50)
    private String busRoute;

    @Column(name = "bus_stop", length = 100)
    private String busStop;

    @Column(name = "bus_number", length = 20)
    private String busNumber;

    @Column(name = "driver_name", length = 100)
    private String driverName;

    @Column(name = "driver_contact", length = 20)
    private String driverContact;

    @Column(name = "pickup_time")
    private String pickupTime;

    @Column(name = "drop_time")
    private String dropTime;

    @Column(name = "transport_fee")
    private Double transportFee;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_fee_status")
    private FeeStatus transportFeeStatus = FeeStatus.PENDING;

    // ========== FEE INFORMATION (NEW) ==========
    @Column(name = "total_fee")
    private Double totalFee;

    @Column(name = "paid_amount")
    @Builder.Default
    private Double paidAmount = 0.0;

    @Column(name = "pending_amount")
    private Double pendingAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_status")
    @Builder.Default
    private FeeStatus feeStatus = FeeStatus.PENDING;

    @Column(name = "tuition_fee")
    private Double tuitionFee;

    @Column(name = "admission_fee")
    private Double admissionFee;

    @Column(name = "examination_fee")
    private Double examinationFee;

    @Column(name = "other_fees")
    private Double otherFees;

    // Add this field to your Student entity
    @Column(name = "fee_due_date")
    private LocalDate feeDueDate;
    // ===========================================

    // Status
    @Enumerated(EnumType.STRING)
    private StudentStatus status = StudentStatus.ACTIVE;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // Relationships
    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private Set<FamilyMember> familyMembers = new HashSet<>();

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private Set<MedicalRecord> medicalRecords = new HashSet<>();

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private Set<Achievement> achievements = new HashSet<>();

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private Set<StudentInterest> interests = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        calculateFeeDetails();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        calculateFeeDetails();
    }

    private void calculateFeeDetails() {
        if (totalFee != null && paidAmount != null) {
            this.pendingAmount = Math.max(0, totalFee - paidAmount);
            if (paidAmount >= totalFee) {
                this.feeStatus = FeeStatus.PAID;
            } else if (paidAmount > 0) {
                this.feeStatus = FeeStatus.PENDING;
            } else {
                this.feeStatus = FeeStatus.PENDING;
            }
        }
    }

    public enum Gender {
        MALE, FEMALE, OTHER
    }

    public enum BloodGroup {
        A_PLUS, A_MINUS, B_PLUS, B_MINUS, O_PLUS, O_MINUS, AB_PLUS, AB_MINUS, UNKNOWN
    }

    public enum Category {
        GENERAL, OBC, SC, ST, OTHER
    }

    public enum TransportMode {
        SCHOOL_BUS, PRIVATE_VEHICLE, PUBLIC_TRANSPORT, WALKING, OTHER
    }

    public enum FeeStatus {
        PAID, PENDING, OVERDUE
    }

    public enum StudentStatus {
        ACTIVE, GRADUATED, TRANSFERRED, INACTIVE
    }
}