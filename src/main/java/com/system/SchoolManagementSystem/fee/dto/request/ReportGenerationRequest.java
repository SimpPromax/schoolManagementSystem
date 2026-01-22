package com.system.SchoolManagementSystem.fee.dto.request;

import com.system.SchoolManagementSystem.fee.enums.ReportFormat;
import com.system.SchoolManagementSystem.fee.enums.ReportType;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@Data
public class ReportGenerationRequest {
    @NotNull
    private ReportType reportType;

    @NotNull
    private ReportFormat format;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private List<String> grades;
    private List<String> classes;
    private Boolean includeCharts = true;
    private String customFilters;
}