package com.system.SchoolManagementSystem.fee.dto.response;

import lombok.Data;

import java.util.Map;

@Data
public class FeeCollectionStatsResponse {
    private Double totalCollected;
    private Double totalPending;
    private Double totalFee;
    private Double collectionRate;

    private Long paidStudents;
    private Long overdueStudents;
    private Long pendingStudents;
    private Long partialPaidStudents;

    private Long remindersSentToday;
    private Long totalRemindersSent;

    private Double todayCollection;
    private Double weeklyCollection;
    private Double monthlyCollection;

    private Map<String, Long> statusDistribution;
    private Map<String, Double> gradeWiseCollection;

    private Long multiplePaymentStudents;
    private Double averagePaymentsPerStudent;

    private Double targetAchievementRate;
    private Double remainingTarget;
}