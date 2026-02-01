package com.system.SchoolManagementSystem.transaction.service;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.termmanagement.dto.request.PaymentApplicationRequest;
import com.system.SchoolManagementSystem.termmanagement.dto.response.PaymentApplicationResponse;
import com.system.SchoolManagementSystem.termmanagement.service.TermFeeService;
import com.system.SchoolManagementSystem.transaction.entity.BankTransaction;
import com.system.SchoolManagementSystem.transaction.entity.PaymentTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class StudentFeeUpdateService {

    private final StudentRepository studentRepository;
    private final TermFeeService termFeeService;

    // Concurrency control - one lock per student
    private final Map<Long, ReentrantLock> studentLocks = new ConcurrentHashMap<>();

    // ========== SINGLE SOURCE OF TRUTH METHODS ==========

    /**
     * MAIN ENTRY POINT - Single source of truth for all fee updates
     */
    @Transactional
    public PaymentApplicationResponse updateStudentFeeFromPayment(
            Long studentId,
            Double amount,
            String reference,
            String notes,
            String source) {

        // Get or create lock for this student
        ReentrantLock lock = studentLocks.computeIfAbsent(studentId, k -> new ReentrantLock());
        lock.lock();

        try {
            log.info("🔄 [SINGLE-SOURCE] Updating fee for student {}: ₹{} via {} (Ref: {})",
                    studentId, amount, source, reference);

            // 1. Validate student can receive payment
            validateStudentForPayment(studentId);

            // 2. First update term fee items (FIFO logic)
            PaymentApplicationRequest feeRequest = new PaymentApplicationRequest();
            feeRequest.setStudentId(studentId);
            feeRequest.setAmount(amount);
            feeRequest.setReference(reference);
            feeRequest.setNotes(notes != null ? notes : source);
            feeRequest.setApplyToFutureTerms(true);

            PaymentApplicationResponse termFeeResponse = termFeeService.applyPaymentToStudent(feeRequest);

            // 3. Then update student summary (optimized - no full recalculation)
            updateStudentFeeSummaryOptimized(studentId);

            log.info("✅ [SINGLE-SOURCE] Fee updated: Applied=₹{}, Remaining=₹{}, AllPaid={}",
                    termFeeResponse.getAppliedPayment(),
                    termFeeResponse.getRemainingPayment(),
                    termFeeResponse.getAllPaid());

            return termFeeResponse;

        } finally {
            lock.unlock();
            // Clean up lock if no one is waiting
            if (!lock.isLocked() && lock.getQueueLength() == 0) {
                studentLocks.remove(studentId);
            }
        }
    }

    /**
     * Update from bank transaction
     */
    @Transactional
    public PaymentApplicationResponse updateFeeFromBankTransaction(BankTransaction bankTransaction) {
        if (bankTransaction.getStudent() == null) {
            throw new IllegalArgumentException("Bank transaction has no student assigned");
        }

        return updateStudentFeeFromPayment(
                bankTransaction.getStudent().getId(),
                bankTransaction.getAmount(),
                bankTransaction.getBankReference(),
                "Auto-matched from bank import",
                "BANK_TRANSACTION"
        );
    }

    /**
     * Update from payment transaction
     */
    @Transactional
    public PaymentApplicationResponse updateFeeFromPaymentTransaction(PaymentTransaction paymentTransaction) {
        if (paymentTransaction.getStudent() == null) {
            throw new IllegalArgumentException("Payment transaction has no student assigned");
        }

        return updateStudentFeeFromPayment(
                paymentTransaction.getStudent().getId(),
                paymentTransaction.getAmount(),
                paymentTransaction.getReceiptNumber(),
                paymentTransaction.getNotes(),
                "PAYMENT_TRANSACTION"
        );
    }

    /**
     * Manual payment application
     */
    @Transactional
    public PaymentApplicationResponse applyManualPayment(Long studentId, Double amount,
                                                         String reference, String notes) {
        return updateStudentFeeFromPayment(
                studentId,
                amount,
                reference,
                notes,
                "MANUAL_PAYMENT"
        );
    }

    /**
     * Batch update multiple payments (optimized)
     */
    @Transactional
    public Map<Long, PaymentApplicationResponse> batchUpdateFees(
            Map<Long, List<PaymentApplicationRequest>> paymentsByStudent) {

        log.info("🔄 [BATCH] Updating fees for {} students", paymentsByStudent.size());
        Map<Long, PaymentApplicationResponse> results = new ConcurrentHashMap<>();

        paymentsByStudent.forEach((studentId, payments) -> {
            try {
                double totalAmount = payments.stream()
                        .mapToDouble(PaymentApplicationRequest::getAmount)
                        .sum();

                if (totalAmount > 0) {
                    PaymentApplicationResponse response = updateStudentFeeFromPayment(
                            studentId,
                            totalAmount,
                            "BATCH_" + System.currentTimeMillis(),
                            "Batch payment update",
                            "BATCH_UPDATE"
                    );
                    results.put(studentId, response);
                }
            } catch (Exception e) {
                log.error("❌ Failed batch update for student {}: {}", studentId, e.getMessage());

            }
        });

        return results;
    }

    // ========== OPTIMIZED UPDATE METHODS ==========

    /**
     * Optimized student fee summary update (no full recalculation)
     */
    private void updateStudentFeeSummaryOptimized(Long studentId) {
        try {
            // Get fresh data from database with optimized query
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

            // Use database view or direct calculation
            recalculateStudentFeeFromView(student);

            student.setLastFeeUpdate(LocalDateTime.now());
            studentRepository.save(student);

            log.debug("✅ Optimized fee summary updated for student {}", studentId);

        } catch (Exception e) {
            log.error("❌ Failed to update fee summary for student {}: {}", studentId, e.getMessage());
            // Fallback to full recalculation
            updateStudentFeeSummaryFallback(studentId);
        }
    }

    /**
     * Recalculate from database view (optimized)
     */
    private void recalculateStudentFeeFromView(Student student) {
        // This would use a materialized view in production
        // For now, use optimized calculation

        Double totalFee = student.getTermAssignments().stream()
                .mapToDouble(ta -> ta.getTotalTermFee() != null ? ta.getTotalTermFee() : 0.0)
                .sum();

        Double paidAmount = student.getTermAssignments().stream()
                .mapToDouble(ta -> ta.getPaidAmount() != null ? ta.getPaidAmount() : 0.0)
                .sum();

        Double pendingAmount = Math.max(0, totalFee - paidAmount);

        // Update only what changed
        student.setTotalFee(totalFee);
        student.setPaidAmount(paidAmount);
        student.setPendingAmount(pendingAmount);

        // Update fee status (simplified)
        if (paidAmount >= totalFee) {
            student.setFeeStatus(Student.FeeStatus.PAID);
        } else if (paidAmount > 0) {
            // Check for overdue
            boolean hasOverdue = student.getTermAssignments().stream()
                    .anyMatch(ta -> ta.getTermFeeStatus() ==
                            com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment.FeeStatus.OVERDUE);
            student.setFeeStatus(hasOverdue ? Student.FeeStatus.OVERDUE : Student.FeeStatus.PARTIAL);
        } else {
            student.setFeeStatus(Student.FeeStatus.PENDING);
        }
    }

    /**
     * Fallback method - uses full recalculation
     */
    private void updateStudentFeeSummaryFallback(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

        student.updateFeeSummary();
        student.setLastFeeUpdate(LocalDateTime.now());
        studentRepository.save(student);

        log.warn("⚠️ Used fallback fee update for student {}", studentId);
    }

    // ========== REVERSAL METHODS ==========

    /**
     * Revert a fee payment
     */
    @Transactional
    public PaymentApplicationResponse revertPayment(Long studentId, Double amount,
                                                    String reference, String reason) {

        log.warn("↩️ [REVERT] Reverting payment for student {}: ₹{} (Reason: {})",
                studentId, amount, reason);

        // Create negative payment request
        PaymentApplicationRequest revertRequest = new PaymentApplicationRequest();
        revertRequest.setStudentId(studentId);
        revertRequest.setAmount(-amount); // Negative amount for reversal
        revertRequest.setReference("REVERT_" + reference);
        revertRequest.setNotes("Payment reversal: " + reason);
        revertRequest.setApplyToFutureTerms(false);

        PaymentApplicationResponse revertResponse = termFeeService.applyPaymentToStudent(revertRequest);

        // Update student summary
        updateStudentFeeSummaryOptimized(studentId);

        return revertResponse;
    }

    // ========== VALIDATION METHODS ==========

    /**
     * Validate if student can receive payment
     */
    public void validateStudentForPayment(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

        // Check student status
        if (student.getStatus() != Student.StudentStatus.ACTIVE) {
            throw new IllegalStateException(
                    String.format("Student %s is not active (status: %s)",
                            student.getFullName(), student.getStatus()));
        }


    }

    /**
     * Get student fee summary (cached/optimized)
     */
    public StudentFeeSummary getStudentFeeSummary(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

        return StudentFeeSummary.builder()
                .studentId(studentId)
                .studentName(student.getFullName())
                .grade(student.getGrade())
                .totalFee(student.getTotalFee() != null ? student.getTotalFee() : 0.0)
                .paidAmount(student.getPaidAmount() != null ? student.getPaidAmount() : 0.0)
                .pendingAmount(student.getPendingAmount() != null ? student.getPendingAmount() : 0.0)
                .feeStatus(student.getFeeStatus())
                .lastFeeUpdate(student.getLastFeeUpdate())
                .hasOverdueFees(student.hasOverdueFees())
                .overdueAmount(student.getOverdueAmount())
                .paymentPercentage(student.getPaymentPercentage())
                .build();
    }

    /**
     * Force refresh of student fee data
     */
    @Transactional
    public void refreshStudentFeeData(Long studentId) {
        log.info("🔄 Force refreshing fee data for student {}", studentId);

        // Evict any cache
        // cacheService.evictStudentFeeCache(studentId);

        // Update from database
        updateStudentFeeSummaryFallback(studentId);
    }

    // ========== DTO CLASSES ==========

    @lombok.Data
    @lombok.Builder
    public static class StudentFeeSummary {
        private Long studentId;
        private String studentName;
        private String grade;
        private Double totalFee;
        private Double paidAmount;
        private Double pendingAmount;
        private Student.FeeStatus feeStatus;
        private LocalDateTime lastFeeUpdate;
        private Boolean hasOverdueFees;
        private Double overdueAmount;
        private Double paymentPercentage;

        public String getPaymentProgress() {
            return String.format("%.1f%%", paymentPercentage != null ? paymentPercentage : 0.0);
        }
    }
}