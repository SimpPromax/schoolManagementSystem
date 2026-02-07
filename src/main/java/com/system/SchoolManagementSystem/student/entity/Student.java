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

    // ========== NEW: MANUAL DUE DATE FLAG ==========
    @Transient
    @Builder.Default
    private boolean manualDueDateUpdate = false;

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

        // Skip automatic due date calculation if it was manually set
        if (!manualDueDateUpdate) {
            calculateFeeDetails();
        } else {
            // Reset the flag after processing
            manualDueDateUpdate = false;
            // Still calculate other fee details but preserve due date
            calculateFeeDetailsWithoutDueDate();
        }
    }

    /**
     * Calculate fee details WITHOUT updating due date
     * Used when due date is manually set
     */
    private void calculateFeeDetailsWithoutDueDate() {
        try {
            LocalDate preservedDueDate = this.feeDueDate;

            // Calculate totals but preserve due date
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

                // Update fields (except due date)
                this.totalFee = totalTermFee;
                this.paidAmount = totalPaid;
                this.pendingAmount = totalPending;
                this.tuitionFee = tuitionTotal;
                this.admissionFee = admissionTotal;
                this.examinationFee = examinationTotal;
                this.otherFees = otherTotal;

                // Update fee status based on current due date and payments
                updateFeeStatusBasedOnCurrentDueDate();

            } else {
                // If termAssignments not initialized, keep existing values or set defaults
                this.pendingAmount = this.totalFee != null ?
                        Math.max(0, this.totalFee - (this.paidAmount != null ? this.paidAmount : 0.0)) : 0.0;

                // Update status based on current due date
                updateFeeStatusBasedOnCurrentDueDate();
            }

            // Restore the preserved due date
            this.feeDueDate = preservedDueDate;

        } catch (Exception e) {
            // Log error but don't throw
            System.err.println("Error calculating fee details without due date: " + e.getMessage());
        }
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

                // ========== SMART DUE DATE MANAGEMENT ==========
                // Update fee due date (earliest pending due date)
                Optional<LocalDate> earliestDueDate = getEarliestPendingDueDate();
                this.feeDueDate = earliestDueDate.orElse(null);

                // Update fee status SMARTLY based on due date and payments
                updateFeeStatusBasedOnDueDate();

            } else {
                // If termAssignments not initialized, keep existing values or set defaults
                this.pendingAmount = this.totalFee != null ?
                        Math.max(0, this.totalFee - (this.paidAmount != null ? this.paidAmount : 0.0)) : 0.0;

                // Still update status based on current data
                updateFeeStatusBasedOnDueDate();
            }
        } catch (Exception e) {
            // Log error but don't throw - we can still function without term assignments
            System.err.println("Error calculating fee details: " + e.getMessage());
        }
    }

    /**
     * Update fee status based on due date and payment status
     */
    private void updateFeeStatusBasedOnDueDate() {
        // If all fees are paid, status is PAID (regardless of due date)
        if (this.pendingAmount != null && this.pendingAmount <= 0) {
            this.feeStatus = FeeStatus.PAID;
            return;
        }

        // If no pending amount but no total fee either
        if (this.totalFee == null || this.totalFee <= 0) {
            this.feeStatus = FeeStatus.PAID;
            return;
        }

        // Check if we have a due date
        if (this.feeDueDate == null) {
            // No due date but has pending fees
            if (this.paidAmount != null && this.paidAmount > 0) {
                this.feeStatus = FeeStatus.PARTIAL;
            } else {
                this.feeStatus = FeeStatus.PENDING;
            }
            return;
        }

        LocalDate today = LocalDate.now();

        // Check if due date has passed
        if (today.isAfter(this.feeDueDate)) {
            // Due date passed = OVERDUE (if there's pending amount)
            if (this.pendingAmount != null && this.pendingAmount > 0) {
                this.feeStatus = FeeStatus.OVERDUE;
            } else {
                // Somehow due date passed but nothing pending
                this.feeStatus = FeeStatus.PAID;
            }
        } else {
            // Due date not passed yet
            if (this.paidAmount != null && this.paidAmount > 0) {
                this.feeStatus = FeeStatus.PARTIAL;
            } else {
                this.feeStatus = FeeStatus.PENDING;
            }
        }
    }

    /**
     * Update fee status based on CURRENT due date (without recalculating due date)
     */
    private void updateFeeStatusBasedOnCurrentDueDate() {
        // If all fees are paid, status is PAID (regardless of due date)
        if (this.pendingAmount != null && this.pendingAmount <= 0) {
            this.feeStatus = FeeStatus.PAID;
            return;
        }

        // If no pending amount but no total fee either
        if (this.totalFee == null || this.totalFee <= 0) {
            this.feeStatus = FeeStatus.PAID;
            return;
        }

        // Check if we have a due date
        if (this.feeDueDate == null) {
            // No due date but has pending fees
            if (this.paidAmount != null && this.paidAmount > 0) {
                this.feeStatus = FeeStatus.PARTIAL;
            } else {
                this.feeStatus = FeeStatus.PENDING;
            }
            return;
        }

        LocalDate today = LocalDate.now();

        // Check if due date has passed
        if (today.isAfter(this.feeDueDate)) {
            // Due date passed = OVERDUE (if there's pending amount)
            if (this.pendingAmount != null && this.pendingAmount > 0) {
                this.feeStatus = FeeStatus.OVERDUE;
            } else {
                // Somehow due date passed but nothing pending
                this.feeStatus = FeeStatus.PAID;
            }
        } else {
            // Due date not passed yet
            if (this.paidAmount != null && this.paidAmount > 0) {
                this.feeStatus = FeeStatus.PARTIAL;
            } else {
                this.feeStatus = FeeStatus.PENDING;
            }
        }
    }

    // ========== NEW: MANUAL DUE DATE METHODS ==========

    /**
     * Set due date manually (bypasses automatic calculation)
     */
    public void setFeeDueDateManually(LocalDate dueDate) {
        this.feeDueDate = dueDate;
        this.manualDueDateUpdate = true;
        updateFeeStatusBasedOnCurrentDueDate();
    }

    /**
     * Clear due date manually
     */
    public void clearFeeDueDateManually() {
        this.feeDueDate = null;
        this.manualDueDateUpdate = true;
        if (this.pendingAmount != null && this.pendingAmount <= 0) {
            this.feeStatus = FeeStatus.PAID;
        } else if (this.paidAmount != null && this.paidAmount > 0) {
            this.feeStatus = FeeStatus.PARTIAL;
        } else {
            this.feeStatus = FeeStatus.PENDING;
        }
    }

    /**
     * Update due date from term (sets manual flag)
     */
    public void updateDueDateFromTerm(LocalDate termDueDate) {
        setFeeDueDateManually(termDueDate);
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

        LocalDate earliestDate = null;

        // Check term assignment due dates
        for (StudentTermAssignment assignment : this.termAssignments) {
            if (assignment.getPendingAmount() > 0 && assignment.getDueDate() != null) {
                if (earliestDate == null || assignment.getDueDate().isBefore(earliestDate)) {
                    earliestDate = assignment.getDueDate();
                }
            }

            // Also check individual fee items for due dates
            if (Hibernate.isInitialized(assignment.getFeeItems())) {
                for (var feeItem : assignment.getFeeItems()) {
                    if (feeItem.getPendingAmount() != null &&
                            feeItem.getPendingAmount() > 0 &&
                            feeItem.getDueDate() != null) {

                        if (earliestDate == null || feeItem.getDueDate().isBefore(earliestDate)) {
                            earliestDate = feeItem.getDueDate();
                        }
                    }
                }
            }
        }

        return Optional.ofNullable(earliestDate);
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

    // ========== NEW HELPER METHODS FOR SMART DUE DATE MANAGEMENT ==========

    /**
     * Check if due date is overdue
     */
    @Transient
    public boolean isDueDateOverdue() {
        if (this.feeDueDate == null) return false;
        return LocalDate.now().isAfter(this.feeDueDate) &&
                this.pendingAmount != null &&
                this.pendingAmount > 0;
    }

    /**
     * Get days until due date (negative if overdue)
     */
    @Transient
    public Long getDaysUntilDueDate() {
        if (this.feeDueDate == null) return null;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), this.feeDueDate);
    }

    /**
     * Get overdue days count
     */
    @Transient
    public Long getOverdueDays() {
        if (!isDueDateOverdue()) return 0L;
        return Math.abs(getDaysUntilDueDate());
    }

    /**
     * Clear due date when all fees are paid
     * Call this after successful payment
     */
    public void clearDueDateIfPaid() {
        if (this.pendingAmount != null && this.pendingAmount <= 0) {
            this.feeDueDate = null;
            this.feeStatus = FeeStatus.PAID;
        }
    }

    /**
     * Force refresh of due date and status
     * Useful when term assignments change outside normal flow
     */
    public void refreshFeeStatusAndDueDate() {
        calculateFeeDetails();
    }

    /**
     * Check if student needs due date update
     */
    @Transient
    public boolean needsDueDateUpdate() {
        if (this.feeDueDate == null && (this.pendingAmount == null || this.pendingAmount <= 0)) {
            return false; // Already cleared
        }

        Optional<LocalDate> calculatedDueDate = getEarliestPendingDueDate();
        LocalDate currentDueDate = this.feeDueDate;

        if (calculatedDueDate.isPresent() && currentDueDate == null) {
            return true; // Should have a due date but doesn't
        }

        if (!calculatedDueDate.isPresent() && currentDueDate != null) {
            return true; // Shouldn't have a due date but does
        }

        if (calculatedDueDate.isPresent() && currentDueDate != null) {
            return !calculatedDueDate.get().equals(currentDueDate); // Dates differ
        }

        return false;
    }

    /**
     * Check if due date was manually set
     */
    @Transient
    public boolean isDueDateManuallySet() {
        return this.manualDueDateUpdate;
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

    /**
     * Get due date status description
     */
    @Transient
    public String getDueDateStatus() {
        if (this.feeDueDate == null) {
            return "NO_DUE_DATE";
        }

        LocalDate today = LocalDate.now();

        if (today.isAfter(this.feeDueDate)) {
            return "OVERDUE";
        } else if (today.isEqual(this.feeDueDate)) {
            return "DUE_TODAY";
        } else if (today.plusDays(3).isAfter(this.feeDueDate)) {
            return "DUE_SOON";
        } else {
            return "UPCOMING";
        }
    }

    /**
     * Check if payment is urgent (due within 3 days or overdue)
     */
    @Transient
    public boolean isPaymentUrgent() {
        if (this.feeDueDate == null) return false;

        LocalDate today = LocalDate.now();
        return today.isAfter(this.feeDueDate) ||
                !today.plusDays(3).isBefore(this.feeDueDate);
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