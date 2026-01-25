package com.system.SchoolManagementSystem.termmanagement.dto.request;

import com.system.SchoolManagementSystem.transaction.enums.FeeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class BulkUpdateRequest {

    @NotNull(message = "Student IDs are required")
    @Size(min = 1, message = "At least one student ID is required")
    private List<Long> studentIds;

    @NotBlank(message = "Academic year is required")
    private String academicYear;

    @NotNull(message = "New status is required")
    private FeeStatus newStatus;

    @NotBlank(message = "Updated by field is required")
    private String updatedBy;

    private String notes;
}