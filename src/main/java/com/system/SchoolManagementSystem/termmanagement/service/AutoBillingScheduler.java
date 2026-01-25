package com.system.SchoolManagementSystem.termmanagement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoBillingScheduler {

    private final TermFeeService termFeeService;
    private final TermService termService;

    /**
     * Check for new term start and auto-bill
     * Runs daily at 6 AM
     */
    @Scheduled(cron = "0 0 6 * * ?")
    public void checkAndAutoBillNewTerm() {
        log.info("🔄 Checking for new term to auto-bill...");

        try {
            // First update term statuses
            termService.updateTermStatus();

            // Then auto-bill current term
            termFeeService.autoBillCurrentTerm();

        } catch (Exception e) {
            log.error("❌ Error in auto-billing scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Update overdue status daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void updateOverdueStatus() {
        log.info("🔄 Updating overdue fee status...");

        // This would update fee items and assignments that are overdue
        // Implementation would mark items as OVERDUE if due date has passed
    }

    /**
     * Send fee reminders (weekly on Monday at 9 AM)
     */
    @Scheduled(cron = "0 0 9 * * MON")
    public void sendFeeReminders() {
        log.info("📧 Sending fee reminders...");

        // This would send SMS/email reminders for upcoming due dates
        // Implementation would query for fees due in next 7 days
    }
}