package com.system.SchoolManagementSystem.termmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FeeStructureResponse {

    private Long id;
    private Long termId;
    private String termName;
    private String academicYear;
    private String grade;

    // Fee components
    private Double tuitionFee;
    private Double basicFee;
    private Double examinationFee;
    private Double transportFee;
    private Double libraryFee;
    private Double sportsFee;
    private Double activityFee;
    private Double hostelFee;
    private Double uniformFee;
    private Double bookFee;
    private Double otherFees;
    private Double totalFee;

    private Boolean isActive;
    private String status;

    // Statistics
    private Integer studentCount;
    private Double totalExpectedRevenue;
    private Double totalCollected;
    private Double collectionRate;

    // Audit information
    private String createdBy;
    private String updatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // Version information
    private Integer version;
    private String changeDescription;

    // Historical data
    private Double previousTotalFee;
    private Double percentageChange;

    // Applicable categories
    private Boolean appliesToBoarding;
    private Boolean appliesToDayScholars;
    private Boolean appliesToTransportUsers;
}