package com.system.SchoolManagementSystem.student.dto;

import lombok.Data;

@Data
public class GradeStatisticsDTO {
    private String grade;
    private Integer enrolled;
    private Integer paidStudents;
    private Integer partialStudents;
    private Integer pendingStudents;
    private Integer overdueStudents;
    private Double totalFee;
    private Double paidAmount;
    private Double pendingAmount;
    private Double collectionRate;

    // Add this constructor - it must match the JPQL query
    public GradeStatisticsDTO(
            String grade,
            Long enrolled,        // COUNT returns Long
            Long paidStudents,    // SUM returns Long
            Long partialStudents, // SUM returns Long
            Long pendingStudents, // SUM returns Long
            Long overdueStudents, // SUM returns Long
            Double totalFee,
            Double paidAmount,
            Double pendingAmount,
            Double collectionRate) {
        this.grade = grade;
        this.enrolled = enrolled != null ? enrolled.intValue() : 0;
        this.paidStudents = paidStudents != null ? paidStudents.intValue() : 0;
        this.partialStudents = partialStudents != null ? partialStudents.intValue() : 0;
        this.pendingStudents = pendingStudents != null ? pendingStudents.intValue() : 0;
        this.overdueStudents = overdueStudents != null ? overdueStudents.intValue() : 0;
        this.totalFee = totalFee != null ? totalFee : 0.0;
        this.paidAmount = paidAmount != null ? paidAmount : 0.0;
        this.pendingAmount = pendingAmount != null ? pendingAmount : 0.0;
        this.collectionRate = collectionRate != null ? collectionRate : 0.0;
    }

    // Optional: Add a default constructor
    public GradeStatisticsDTO() {
    }
}