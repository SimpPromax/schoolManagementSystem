package com.system.SchoolManagementSystem.termmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RegenerateBillResponse {

    private Long studentId;
    private String studentName;
    private String studentCode;
    private Long termId;
    private String termName;
    private String academicYear;

    private Boolean regenerated;
    private String regenerationType; // FULL, PARTIAL, ADJUSTED

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime regeneratedAt;

    // Financial details
    private Double previousTotalFee;
    private Double newTotalFee;
    private Double difference;
    private String differenceReason;

    // Fee items details
    private Integer previousItemsCount;
    private Integer newItemsCount;
    private List<FeeItemComparison> itemComparison;

    // Assignment details
    private AssignmentDetails previousAssignment;
    private AssignmentDetails newAssignment;

    // Audit information
    private String regeneratedBy;
    private String reasonForRegeneration;
    private String referenceNumber;

    @Data
    public static class FeeItemComparison {
        private String itemName;
        private String feeType;
        private Double previousAmount;
        private Double newAmount;
        private Double difference;
        private String changeType; // ADDED, REMOVED, MODIFIED, UNCHANGED
        private String changeReason;
    }

    @Data
    public static class AssignmentDetails {
        private Long assignmentId;
        private Double totalFee;
        private Double paidAmount;
        private Double pendingAmount;
        private String feeStatus;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private java.time.LocalDate dueDate;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private java.time.LocalDate billingDate;

        private Integer feeItemsCount;
        private Boolean isBilled;
    }
}