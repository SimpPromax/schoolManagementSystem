package com.system.SchoolManagementSystem.termmanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class ReminderRequest {

    @NotNull(message = "Student IDs are required")
    @Size(min = 1, message = "At least one student ID is required")
    private List<Long> studentIds;

    @NotBlank(message = "Reminder type is required")
    private String reminderType; // SMS, EMAIL, BOTH

    private String customMessage;

    private Boolean includePaymentLink = true;

    @NotBlank(message = "Sender name is required")
    private String senderName = "School Administration";

    private String urgencyLevel = "MEDIUM"; // LOW, MEDIUM, HIGH

    @NotNull(message = "Send date is required")
    private java.time.LocalDate sendDate = java.time.LocalDate.now();
}