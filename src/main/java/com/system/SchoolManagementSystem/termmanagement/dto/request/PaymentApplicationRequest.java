package com.system.SchoolManagementSystem.termmanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentApplicationRequest {
    @NotNull(message = "Student ID is required")
    private Long studentId;

    @NotNull(message = "Amount is required")
    private Double amount;

    @NotBlank(message = "Payment reference is required")
    private String reference;

    private String notes;
    private Boolean applyToFutureTerms = false;
}