package com.system.SchoolManagementSystem.termmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class AcademicYearHistoryResponse {
    private String academicYear;
    private List<TermResponse> terms;
    private Integer totalStudents;
    private Double totalExpected;
    private Double totalCollections;
    private Double collectionRate;
    private Boolean isCurrent;
    private Integer termCount;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate yearStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate yearEndDate;
}