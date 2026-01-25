package com.system.SchoolManagementSystem.termmanagement.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TermBreakUpdateResponse {
    private Long termId;
    private String termName;
    private List<LocalDate> previousBreaks;
    private List<LocalDate> updatedBreaks;
    private String breakDescription;
    private LocalDateTime updatedAt;
    private String message;
}