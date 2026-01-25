package com.system.SchoolManagementSystem.termmanagement.dto.response;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AcademicYearInitializationResponse {
    private String academicYear;
    private List<TermResponse> createdTerms;
    private LocalDateTime initializedAt;
    private String status;
    private String message;
}