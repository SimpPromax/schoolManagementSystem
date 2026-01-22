package com.system.SchoolManagementSystem.fee.dto.request;

import com.system.SchoolManagementSystem.student.entity.Student;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class FeeCollectionFilterRequest {
    private String grade;
    private String className;
    private Student.FeeStatus feeStatus;
    private String searchQuery;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    private Integer page = 0;
    private Integer size = 50;
    private String sortBy = "createdAt";
    private String sortDirection = "DESC";
}