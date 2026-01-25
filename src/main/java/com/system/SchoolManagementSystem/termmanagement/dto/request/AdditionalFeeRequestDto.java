package com.system.SchoolManagementSystem.termmanagement.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.system.SchoolManagementSystem.termmanagement.entity.TermFeeItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class AdditionalFeeRequestDto {
    @NotNull(message = "Student ID is required")
    private Long studentId;

    @NotNull(message = "Term ID is required")
    private Long termId;

    @NotBlank(message = "Item name is required")
    private String itemName;

    @NotNull(message = "Fee type is required")
    private TermFeeItem.FeeType feeType;

    @NotNull(message = "Amount is required")
    private Double amount;

    @NotNull(message = "Due date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    private Boolean isMandatory = true;
    private String notes;
    private String itemType = "ADDITIONAL";
}