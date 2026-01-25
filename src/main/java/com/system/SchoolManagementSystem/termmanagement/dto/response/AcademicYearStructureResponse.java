package com.system.SchoolManagementSystem.termmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class AcademicYearStructureResponse {
    private String academicYear;
    private Boolean isInitialized;
    private String currentTermName;
    private Long currentTermId;
    private String message;

    private List<TermStructure> terms;

    @Data
    public static class TermStructure {
        private Long termId;
        private String termName;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate startDate;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate endDate;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate feeDueDate;

        private String status;
        private Boolean isCurrent;
        private Integer studentCount;
        private Double expectedRevenue;
        private Double collectedRevenue;
        private Double collectionRate;
        private List<LocalDate> termBreaks;
        private String termBreakDescription;
        private Integer workingDays;
        private Integer breakDays;
    }
}