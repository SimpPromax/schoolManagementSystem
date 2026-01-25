package com.system.SchoolManagementSystem.termmanagement.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class InitializeAcademicYearRequest {

    @NotBlank(message = "Academic year is required")
    @Size(min = 9, max = 9, message = "Academic year must be in format YYYY-YYYY")
    private String academicYear;

    @NotNull(message = "Term periods are required")
    @Size(min = 1, message = "At least one term period is required")
    private List<TermPeriod> termPeriods;

    @Data
    public static class TermPeriod {
        @NotBlank(message = "Term name is required")
        private String termName;

        @NotNull(message = "Start date is required")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate startDate;

        @NotNull(message = "End date is required")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate endDate;

        @NotNull(message = "Fee due date is required")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate feeDueDate;

        private List<LocalDate> termBreaks;

        private String termBreakDescription;

        // Optional: Set if this term should be current
        private Boolean isCurrent;
    }
}