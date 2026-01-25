package com.system.SchoolManagementSystem.termmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.system.SchoolManagementSystem.termmanagement.entity.AcademicTerm;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TermResponse {
    private Long id;
    private String termName;
    private String academicYear;
    private String termCode;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate feeDueDate;

    private String status;
    private Boolean isCurrent;
    private String termBreakDescription;
    private List<LocalDate> termBreaks;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // Additional statistics
    private Integer studentCount;
    private Double expectedRevenue;
    private Double collectedRevenue;
    private Double collectionRate;
    private Integer workingDays;
    private Integer breakDays;

    // Static factory method to convert entity to response
    public static TermResponse fromEntity(AcademicTerm entity) {
        if (entity == null) {
            return null;
        }

        TermResponse response = new TermResponse();
        response.setId(entity.getId());
        response.setTermName(entity.getTermName());
        response.setAcademicYear(entity.getAcademicYear());
        response.setTermCode(entity.getTermCode());
        response.setStartDate(entity.getStartDate());
        response.setEndDate(entity.getEndDate());
        response.setFeeDueDate(entity.getFeeDueDate());

        // Handle null status
        if (entity.getStatus() != null) {
            response.setStatus(entity.getStatus().name());
        } else {
            response.setStatus("UPCOMING");
        }

        // Handle null isCurrent
        response.setIsCurrent(entity.getIsCurrent() != null ? entity.getIsCurrent() : false);

        response.setTermBreakDescription(entity.getTermBreakDescription());
        response.setTermBreaks(entity.getTermBreakDates());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());

        // Calculate additional metrics
        response.setWorkingDays(entity.getWorkingDays());
        response.setBreakDays(entity.getBreakDayCount());

        return response;
    }

    // Simple constructor for basic data
    public TermResponse() {
        this.isCurrent = false;
        this.status = "UPCOMING";
    }

    // Builder for easy creation
    public static TermResponseBuilder builder() {
        return new TermResponseBuilder();
    }

    public static class TermResponseBuilder {
        private final TermResponse response = new TermResponse();

        public TermResponseBuilder id(Long id) {
            response.setId(id);
            return this;
        }

        public TermResponseBuilder termName(String termName) {
            response.setTermName(termName);
            return this;
        }

        public TermResponseBuilder academicYear(String academicYear) {
            response.setAcademicYear(academicYear);
            return this;
        }

        public TermResponseBuilder startDate(LocalDate startDate) {
            response.setStartDate(startDate);
            return this;
        }

        public TermResponseBuilder endDate(LocalDate endDate) {
            response.setEndDate(endDate);
            return this;
        }

        public TermResponseBuilder feeDueDate(LocalDate feeDueDate) {
            response.setFeeDueDate(feeDueDate);
            return this;
        }

        public TermResponseBuilder status(String status) {
            response.setStatus(status != null ? status : "UPCOMING");
            return this;
        }

        public TermResponseBuilder isCurrent(Boolean isCurrent) {
            response.setIsCurrent(isCurrent != null ? isCurrent : false);
            return this;
        }

        public TermResponseBuilder termBreakDescription(String description) {
            response.setTermBreakDescription(description);
            return this;
        }

        public TermResponseBuilder termBreaks(List<LocalDate> termBreaks) {
            response.setTermBreaks(termBreaks);
            return this;
        }

        public TermResponse build() {
            return response;
        }
    }

    // Helper methods
    public boolean isActive() {
        LocalDate today = LocalDate.now();
        return startDate != null && endDate != null &&
                !today.isBefore(startDate) && !today.isAfter(endDate);
    }

    public boolean isOverdue() {
        if (feeDueDate == null) return false;
        return LocalDate.now().isAfter(feeDueDate);
    }

    public String getStatusDisplay() {
        if ("ACTIVE".equals(status)) {
            return "Active";
        } else if ("COMPLETED".equals(status)) {
            return "Completed";
        } else if ("UPCOMING".equals(status)) {
            return "Upcoming";
        } else if ("CANCELLED".equals(status)) {
            return "Cancelled";
        }
        return "Upcoming";
    }

    public String getCurrentStatus() {
        return Boolean.TRUE.equals(isCurrent) ? "Current Term" : "Not Current";
    }

    public int getDurationDays() {
        if (startDate == null || endDate == null) return 0;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    public int getDaysRemaining() {
        if (endDate == null) return 0;
        LocalDate today = LocalDate.now();
        if (today.isAfter(endDate)) return 0;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(today, endDate);
    }

    public int getDaysElapsed() {
        if (startDate == null) return 0;
        LocalDate today = LocalDate.now();
        if (today.isBefore(startDate)) return 0;
        LocalDate endDateForCalc = today.isAfter(endDate) ? endDate : today;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDateForCalc) + 1;
    }

    public double getProgressPercentage() {
        int duration = getDurationDays();
        if (duration == 0) return 0;
        int elapsed = getDaysElapsed();
        return Math.min(100, (elapsed * 100.0) / duration);
    }
}