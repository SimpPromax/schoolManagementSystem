package com.system.SchoolManagementSystem.termmanagement.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "academic_terms")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"gradeTermFees", "studentTermAssignments"})
@Slf4j
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // Add this
public class AcademicTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include // Add this
    private Long id;

    @Column(name = "term_name", nullable = false, length = 50)
    private String termName; // e.g., "Term 1", "Term 2", "Term 3"

    @Column(name = "academic_year", nullable = false, length = 10)
    private String academicYear; // e.g., "2026-2027"

    @Column(name = "term_code", unique = true, length = 20)
    private String termCode; // e.g., "T1-2026"

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "fee_due_date")
    private LocalDate feeDueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TermStatus status = TermStatus.UPCOMING;

    @Column(name = "is_current")
    @Builder.Default
    private Boolean isCurrent = false;

    // TERM BREAKS - Stored as JSON array in TEXT column
    @Column(name = "term_breaks", columnDefinition = "TEXT")
    private String termBreaks; // JSON array of dates: ["2026-04-01", "2026-04-02"]

    @Column(name = "term_break_description", length = 500)
    private String termBreakDescription;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relationships
    @OneToMany(mappedBy = "academicTerm", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    @Builder.Default
    private Set<GradeTermFee> gradeTermFees = new HashSet<>();

    @OneToMany(mappedBy = "academicTerm", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<StudentTermAssignment> studentTermAssignments = new HashSet<>();

    // Custom getter to ensure null safety
    public Boolean getIsCurrent() {
        return isCurrent != null ? isCurrent : false;
    }

    // Custom setter to ensure null safety
    public void setIsCurrent(Boolean isCurrent) {
        this.isCurrent = isCurrent != null ? isCurrent : false;
    }

    // Custom getter for status to ensure null safety
    public TermStatus getStatus() {
        return status != null ? status : TermStatus.UPCOMING;
    }

    // Custom setter for status to ensure null safety
    public void setStatus(TermStatus status) {
        this.status = status != null ? status : TermStatus.UPCOMING;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        updateTermCode();

        // Ensure status is not null
        if (this.status == null) {
            this.status = TermStatus.UPCOMING;
        }

        // Ensure isCurrent is not null
        if (this.isCurrent == null) {
            this.isCurrent = false;
        }

        detectCurrentTerm();

        log.debug("Created term: {} - status: {}, isCurrent: {}",
                termName, this.status, this.isCurrent);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        updateTermCode();

        // Ensure status is not null
        if (this.status == null) {
            this.status = TermStatus.UPCOMING;
        }

        // Ensure isCurrent is not null
        if (this.isCurrent == null) {
            this.isCurrent = false;
        }

        detectCurrentTerm();
    }

    private void updateTermCode() {
        if (termCode == null && termName != null && academicYear != null) {
            String yearPart = academicYear.split("-")[0];
            termCode = termName.replace(" ", "").toUpperCase() + "-" + yearPart;
        }
    }

    private void detectCurrentTerm() {
        LocalDate today = LocalDate.now();
        boolean shouldBeCurrent = !today.isBefore(startDate) && !today.isAfter(endDate);

        // Ensure isCurrent is never null
        if (this.isCurrent == null) {
            this.isCurrent = false;
        }

        if (shouldBeCurrent && !this.isCurrent) {
            // If this term should be current but isn't, deactivate all others
            this.isCurrent = true;
            this.status = TermStatus.ACTIVE;
            log.debug("Term {} marked as current", termName);
        } else if (!shouldBeCurrent && this.isCurrent) {
            this.isCurrent = false;
            if (today.isAfter(endDate)) {
                this.status = TermStatus.COMPLETED;
            } else {
                this.status = TermStatus.UPCOMING;
            }
            log.debug("Term {} marked as not current, status: {}", termName, this.status);
        }
    }

    // HELPER METHODS FOR TERM BREAKS

    /**
     * Get term breaks as List<LocalDate>
     * Returns empty list if no breaks or parsing error
     */
    @Transient
    public List<LocalDate> getTermBreakDates() {
        if (termBreaks == null || termBreaks.trim().isEmpty() || termBreaks.equals("[]")) {
            return new ArrayList<>();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            return mapper.readValue(termBreaks,
                    mapper.getTypeFactory().constructCollectionType(List.class, LocalDate.class));
        } catch (Exception e) {
            log.error("Error parsing term breaks JSON for term {}: {}", this.termName, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Set term breaks from List<LocalDate>
     */
    @Transient
    public void setTermBreakDates(List<LocalDate> breakDates) {
        if (breakDates == null || breakDates.isEmpty()) {
            this.termBreaks = "[]";
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            this.termBreaks = mapper.writeValueAsString(breakDates);
        } catch (Exception e) {
            log.error("Error serializing term breaks to JSON for term {}: {}", this.termName, e.getMessage());
            this.termBreaks = "[]";
        }
    }

    /**
     * Add a single break date
     */
    @Transient
    public void addBreakDate(LocalDate breakDate) {
        List<LocalDate> breaks = getTermBreakDates();
        if (!breaks.contains(breakDate)) {
            breaks.add(breakDate);
            setTermBreakDates(breaks);
        }
    }

    /**
     * Remove a break date
     */
    @Transient
    public void removeBreakDate(LocalDate breakDate) {
        List<LocalDate> breaks = getTermBreakDates();
        breaks.remove(breakDate);
        setTermBreakDates(breaks);
    }

    /**
     * Clear all break dates
     */
    @Transient
    public void clearBreakDates() {
        this.termBreaks = "[]";
    }

    /**
     * Check if a specific date is a term break
     */
    @Transient
    public boolean isBreakDate(LocalDate date) {
        return getTermBreakDates().contains(date);
    }

    /**
     * Get number of break days
     */
    @Transient
    public int getBreakDayCount() {
        return getTermBreakDates().size();
    }

    // BUSINESS LOGIC METHODS

    public boolean isActiveTerm() {
        LocalDate today = LocalDate.now();
        return !today.isBefore(startDate) && !today.isAfter(endDate);
    }

    public boolean isOverdue() {
        if (feeDueDate == null) return false;
        return LocalDate.now().isAfter(feeDueDate);
    }

    /**
     * Get total working days (excluding weekends and breaks)
     * Simplified calculation - can be enhanced
     */
    @Transient
    public int getWorkingDays() {
        LocalDate current = startDate;
        int workingDays = 0;
        List<LocalDate> breaks = getTermBreakDates();

        while (!current.isAfter(endDate)) {
            // Skip weekends (Saturday=6, Sunday=7)
            if (current.getDayOfWeek().getValue() < 6 && !breaks.contains(current)) {
                workingDays++;
            }
            current = current.plusDays(1);
        }

        return workingDays;
    }

    /**
     * Check if break dates are within term dates
     */
    @Transient
    public boolean areBreakDatesValid() {
        List<LocalDate> breaks = getTermBreakDates();
        for (LocalDate breakDate : breaks) {
            if (breakDate.isBefore(startDate) || breakDate.isAfter(endDate)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Helper method to safely check if term is current
     */
    @Transient
    public boolean isCurrentTerm() {
        return Boolean.TRUE.equals(isCurrent);
    }

    /**
     * Builder class to ensure proper defaults
     */
    public static class AcademicTermBuilder {
        private TermStatus status = TermStatus.UPCOMING;
        private Boolean isCurrent = false;
        private Set<GradeTermFee> gradeTermFees = new HashSet<>();
        private Set<StudentTermAssignment> studentTermAssignments = new HashSet<>();

        public AcademicTermBuilder status(TermStatus status) {
            this.status = status != null ? status : TermStatus.UPCOMING;
            return this;
        }

        public AcademicTermBuilder isCurrent(Boolean isCurrent) {
            this.isCurrent = isCurrent != null ? isCurrent : false;
            return this;
        }

        public AcademicTermBuilder gradeTermFees(Set<GradeTermFee> gradeTermFees) {
            if (gradeTermFees != null) {
                this.gradeTermFees = gradeTermFees;
            }
            return this;
        }

        public AcademicTermBuilder studentTermAssignments(Set<StudentTermAssignment> studentTermAssignments) {
            if (studentTermAssignments != null) {
                this.studentTermAssignments = studentTermAssignments;
            }
            return this;
        }
    }

    public enum TermStatus {
        UPCOMING, ACTIVE, COMPLETED, CANCELLED
    }
}