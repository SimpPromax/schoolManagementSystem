package com.system.SchoolManagementSystem.termmanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FeeStructureRequest {
    @NotNull(message = "Term ID is required")
    private Long termId;

    @NotBlank(message = "Grade is required")
    private String grade;

    @NotNull(message = "Tuition fee is required")
    private Double tuitionFee;

    @NotNull(message = "Basic fee is required")
    private Double basicFee;

    private Double examinationFee = 0.0;
    private Double transportFee = 0.0;
    private Double libraryFee = 0.0;
    private Double sportsFee = 0.0;
    private Double activityFee = 0.0;
    private Double hostelFee = 0.0;
    private Double uniformFee = 0.0;
    private Double bookFee = 0.0;
    private Double otherFees = 0.0;

    private Boolean isActive = true;
}