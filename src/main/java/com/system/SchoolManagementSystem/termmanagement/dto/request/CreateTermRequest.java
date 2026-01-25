package com.system.SchoolManagementSystem.termmanagement.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CreateTermRequest {
    @NotBlank(message = "Term name is required")
    private String termName;

    @NotBlank(message = "Academic year is required")
    private String academicYear;

    @NotNull(message = "Start date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate feeDueDate;

    // Added to support setting term as current during creation
    private Boolean isCurrent;

    // Added for term breaks
    private String termBreaks;

    private String termBreakDescription;
}