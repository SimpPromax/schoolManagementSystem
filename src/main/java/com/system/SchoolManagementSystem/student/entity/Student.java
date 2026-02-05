package com.system.SchoolManagementSystem.student.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Table(name = "students")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"familyMembers", "medicalRecords", "achievements", "interests", "termAssignments"})
@SQLDelete(sql = "UPDATE students SET deleted = true WHERE id = ?") // Soft delete
@Where(clause = "deleted = false") // Automatically filter out deleted records
public class Student {

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

    // ========== FEE INFORMATION (UPDATED FOR TERM INTEGRATION) ==========
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

    @Column(name = "fee_due_date")
    private LocalDate feeDueDate;

    @Column(name = "last_fee_update")
    private LocalDateTime lastFeeUpdate;
    // ===========================================

    // Status
    @Enumerated(EnumType.STRING)
    private StudentStatus status = StudentStatus.ACTIVE;

    // ========== SOFT DELETE FIELD ==========
    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ========== EXISTING RELATIONSHIPS ==========
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

    // ========== NEW: TERM FEE RELATIONSHIPS ==========
    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private Set<StudentTermAssignment> termAssignments = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        lastFeeUpdate = LocalDateTime.now();
        calculateFeeDetails();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        lastFeeUpdate = LocalDateTime.now();
        calculateFeeDetails();
    }

    /**
     * Calculate fee details from term assignments
     */
    private void calculateFeeDetails() {
        try {
            // Only calculate if termAssignments is initialized
            if (Hibernate.isInitialized(this.termAssignments) && this.termAssignments != null) {
                // Calculate totals from term assignments
                double totalTermFee = getTotalFeeAmount();
                double totalPaid = getTotalPaidAmount();
                double totalPending = getTotalPendingAmount();

                // Calculate fee breakdown from term assignments
                double tuitionTotal = getTuitionFeeTotal();
                double admissionTotal = getAdmissionFeeTotal();
                double examinationTotal = getExaminationFeeTotal();
                double otherTotal = getOtherFeesTotal();

                // Update fields
                this.totalFee = totalTermFee;
                this.paidAmount = totalPaid;
                this.pendingAmount = totalPending;
                this.tuitionFee = tuitionTotal;
                this.admissionFee = admissionTotal;
                this.examinationFee = examinationTotal;
                this.otherFees = otherTotal;

                // Update fee status
                if (totalPaid >= totalTermFee) {
                    this.feeStatus = FeeStatus.PAID;
                } else if (totalPaid > 0) {
                    this.feeStatus = FeeStatus.PARTIAL;
                } else {
                    this.feeStatus = FeeStatus.PENDING;
                }

                // Update fee due date (earliest pending due date)
                Optional<LocalDate> earliestDueDate = getEarliestPendingDueDate();
                earliestDueDate.ifPresent(date -> this.feeDueDate = date);
            } else {
                // If termAssignments not initialized, keep existing values or set defaults
                this.pendingAmount = this.totalFee != null ?
                        Math.max(0, this.totalFee - (this.paidAmount != null ? this.paidAmount : 0.0)) : 0.0;
            }
        } catch (Exception e) {
            // Log error but don't throw - we can still function without term assignments
            System.err.println("Error calculating fee details: " + e.getMessage());
        }
    }

    // ========== HELPER METHODS FOR TERM FEE CALCULATION ==========

    /**
     * Get total fee amount across all terms
     */
    public Double getTotalFeeAmount() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return 0.0;
        }
        return termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getTotalTermFee)
                .sum();
    }

    /**
     * Get total paid amount across all terms
     */
    public Double getTotalPaidAmount() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return 0.0;
        }
        return termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getPaidAmount)
                .sum();
    }

    /**
     * Get total pending amount across all terms
     */
    public Double getTotalPendingAmount() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return 0.0;
        }
        return termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getPendingAmount)
                .sum();
    }

    /**
     * Get tuition fee total across all terms
     */
    public Double getTuitionFeeTotal() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return 0.0;
        }
        return termAssignments.stream()
                .flatMap(ta -> {
                    if (Hibernate.isInitialized(ta.getFeeItems())) {
                        return ta.getFeeItems().stream();
                    }
                    return Stream.empty();
                })
                .filter(item -> item.getFeeType() != null &&
                        item.getFeeType().name().equals("TUITION"))
                .mapToDouble(item -> item.getAmount())
                .sum();
    }

    /**
     * Get admission fee total across all terms
     */
    public Double getAdmissionFeeTotal() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return 0.0;
        }
        return termAssignments.stream()
                .flatMap(ta -> {
                    if (Hibernate.isInitialized(ta.getFeeItems())) {
                        return ta.getFeeItems().stream();
                    }
                    return Stream.empty();
                })
                .filter(item -> item.getFeeType() != null &&
                        item.getFeeType().name().equals("ADMISSION"))
                .mapToDouble(item -> item.getAmount())
                .sum();
    }

    /**
     * Get examination fee total across all terms
     */
    public Double getExaminationFeeTotal() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return 0.0;
        }
        return termAssignments.stream()
                .flatMap(ta -> {
                    if (Hibernate.isInitialized(ta.getFeeItems())) {
                        return ta.getFeeItems().stream();
                    }
                    return Stream.empty();
                })
                .filter(item -> item.getFeeType() != null &&
                        item.getFeeType().name().equals("EXAMINATION"))
                .mapToDouble(item -> item.getAmount())
                .sum();
    }

    /**
     * Get other fees total across all terms
     */
    public Double getOtherFeesTotal() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return 0.0;
        }
        return termAssignments.stream()
                .flatMap(ta -> {
                    if (Hibernate.isInitialized(ta.getFeeItems())) {
                        return ta.getFeeItems().stream();
                    }
                    return Stream.empty();
                })
                .filter(item -> item.getFeeType() != null &&
                        (item.getFeeType().name().equals("OTHER") ||
                                item.getFeeType().name().equals("LIBRARY") ||
                                item.getFeeType().name().equals("SPORTS") ||
                                item.getFeeType().name().equals("ACTIVITY") ||
                                item.getFeeType().name().equals("HOSTEL") ||
                                item.getFeeType().name().equals("UNIFORM") ||
                                item.getFeeType().name().equals("BOOKS")))
                .mapToDouble(item -> item.getAmount())
                .sum();
    }

    /**
     * Get earliest pending due date from all term assignments
     */
    public Optional<LocalDate> getEarliestPendingDueDate() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return Optional.empty();
        }
        return termAssignments.stream()
                .filter(ta -> ta.getPendingAmount() > 0)
                .map(StudentTermAssignment::getDueDate)
                .filter(date -> date != null)
                .min(LocalDate::compareTo);
    }

    /**
     * Get current term assignment
     */
    public Optional<StudentTermAssignment> getCurrentTermAssignment() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return Optional.empty();
        }
        return termAssignments.stream()
                .filter(ta -> ta.getAcademicTerm() != null &&
                        ta.getAcademicTerm().getIsCurrent())
                .findFirst();
    }

    /**
     * Check if student has overdue fees
     */
    public boolean hasOverdueFees() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return false;
        }
        return termAssignments.stream()
                .anyMatch(ta -> ta.getTermFeeStatus() == StudentTermAssignment.FeeStatus.OVERDUE);
    }

    /**
     * Get overdue amount
     */
    public Double getOverdueAmount() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return 0.0;
        }
        return termAssignments.stream()
                .filter(ta -> ta.getTermFeeStatus() == StudentTermAssignment.FeeStatus.OVERDUE)
                .mapToDouble(StudentTermAssignment::getPendingAmount)
                .sum();
    }

    /**
     * Get payment percentage
     */
    public Double getPaymentPercentage() {
        if (totalFee == null || totalFee <= 0) {
            return 0.0;
        }
        return (paidAmount != null ? paidAmount : 0.0) / totalFee * 100;
    }

    /**
     * Add term assignment
     */
    public void addTermAssignment(StudentTermAssignment assignment) {
        if (Hibernate.isInitialized(this.termAssignments) && this.termAssignments != null) {
            termAssignments.add(assignment);
            assignment.setStudent(this);
            calculateFeeDetails();
        }
    }

    /**
     * Remove term assignment
     */
    public void removeTermAssignment(StudentTermAssignment assignment) {
        if (Hibernate.isInitialized(this.termAssignments) && this.termAssignments != null) {
            termAssignments.remove(assignment);
            assignment.setStudent(null);
            calculateFeeDetails();
        }
    }

    /**
     * Update fee summary from term assignments (call this after payments)
     */
    public void updateFeeSummary() {
        calculateFeeDetails();
    }

    // ========== NEW HELPER METHODS FOR TERM ASSIGNMENTS ==========

    /**
     * Check if student has any term assignments
     * @return true if student has term assignments, false otherwise
     */
    @Transient
    public Boolean getHasTermAssignments() {
        try {
            // Check if the collection proxy is initialized
            if (!Hibernate.isInitialized(this.termAssignments)) {
                // Collection is not initialized - return null instead of trying to initialize
                return null;
            }
            // Now it's safe to check
            return this.termAssignments != null && !this.termAssignments.isEmpty();
        } catch (Exception e) {
            // Catch any exception and return null
            System.err.println("Error in getHasTermAssignments: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get the count of term assignments for this student
     * @return number of term assignments
     */
    @Transient
    public Integer getTermAssignmentCount() {
        try {
            // Check if the collection proxy is initialized
            if (!Hibernate.isInitialized(this.termAssignments)) {
                // Collection is not initialized - return null instead of trying to initialize
                return null;
            }
            // Now it's safe to check size
            return this.termAssignments != null ? this.termAssignments.size() : 0;
        } catch (Exception e) {
            // Catch any exception and return 0
            System.err.println("Error in getTermAssignmentCount: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Get active term assignments (excluding cancelled/waived)
     * @return count of active term assignments
     */
    @Transient
    public Integer getActiveTermAssignmentCount() {
        try {
            if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
                return 0;
            }

            return (int) this.termAssignments.stream()
                    .filter(ta -> ta.getTermFeeStatus() != StudentTermAssignment.FeeStatus.CANCELLED &&
                            ta.getTermFeeStatus() != StudentTermAssignment.FeeStatus.WAIVED)
                    .count();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get term assignments that are pending payment
     * @return count of term assignments with pending/partial/overdue status
     */
    @Transient
    public Integer getPendingTermAssignmentCount() {
        try {
            if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
                return 0;
            }

            return (int) this.termAssignments.stream()
                    .filter(ta -> ta.getTermFeeStatus() == StudentTermAssignment.FeeStatus.PENDING ||
                            ta.getTermFeeStatus() == StudentTermAssignment.FeeStatus.PARTIAL ||
                            ta.getTermFeeStatus() == StudentTermAssignment.FeeStatus.OVERDUE)
                    .count();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Check if student has current term assignment
     * @return true if student has current term assignment
     */
    @Transient
    public Boolean getHasCurrentTermAssignment() {
        return getCurrentTermAssignment().isPresent();
    }

    /**
     * Get total pending amount across all term assignments
     */
    @Transient
    public Double getTotalPendingFromAssignments() {
        try {
            if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
                return 0.0;
            }

            return this.termAssignments.stream()
                    .filter(ta -> ta.getTermFeeStatus() != StudentTermAssignment.FeeStatus.CANCELLED &&
                            ta.getTermFeeStatus() != StudentTermAssignment.FeeStatus.WAIVED)
                    .mapToDouble(StudentTermAssignment::getPendingAmount)
                    .sum();
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Get overdue term assignments
     */
    @Transient
    public List<StudentTermAssignment> getOverdueTermAssignments() {
        try {
            if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
                return Collections.emptyList();
            }

            return this.termAssignments.stream()
                    .filter(ta -> ta.getTermFeeStatus() == StudentTermAssignment.FeeStatus.OVERDUE)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Check if student has overdue term assignments
     */
    @Transient
    public Boolean getHasOverdueTermAssignments() {
        return !getOverdueTermAssignments().isEmpty();
    }

    // ========== ENUMS ==========

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
        PAID, PENDING, OVERDUE, PARTIAL
    }

    public enum StudentStatus {
        ACTIVE, GRADUATED, TRANSFERRED, INACTIVE, SUSPENDED
    }
}