package com.system.SchoolManagementSystem.termmanagement.dto.request;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class TermBreakUpdateRequest {
    private List<LocalDate> termBreaks;
    private String breakDescription;
}