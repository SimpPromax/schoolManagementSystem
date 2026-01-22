package com.system.SchoolManagementSystem.fee.dto.request;

import com.system.SchoolManagementSystem.fee.enums.ReminderChannel;
import com.system.SchoolManagementSystem.fee.enums.ReminderTemplate;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BulkReminderRequest {
    @NotEmpty
    private List<Long> studentIds;

    @NotNull
    private ReminderChannel channel;

    private ReminderTemplate template;

    private String customContent;

    private LocalDateTime scheduleFor;

    private Boolean attachInvoice = true;
}