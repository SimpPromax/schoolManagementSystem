package com.system.SchoolManagementSystem.transaction.service;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.transaction.entity.BankTransaction;
import com.system.SchoolManagementSystem.transaction.entity.PaymentTransaction;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class StudentFeeUpdateService {

    private final StudentRepository studentRepository;

    /**
     * Update student fee payment from a bank transaction
     */
    public Student updateFeeFromBankTransaction(Student student, BankTransaction bankTransaction) {
        if (student == null || bankTransaction == null) {
            log.warn("Cannot update fee: Student or transaction is null");
            return student;
        }

        return updateFeePayment(
                student,
                bankTransaction.getAmount(),
                bankTransaction.getBankReference(),
                "BANK_TX_" + bankTransaction.getId()
        );
    }

    /**
     * Update student fee payment from a verified payment transaction
     */
    public Student updateFeeFromPaymentTransaction(Student student, PaymentTransaction paymentTransaction) {
        if (student == null || paymentTransaction == null) {
            log.warn("Cannot update fee: Student or payment transaction is null");
            return student;
        }

        return updateFeePayment(
                student,
                paymentTransaction.getAmount(),
                paymentTransaction.getReceiptNumber(),
                "PAYMENT_" + paymentTransaction.getId()
        );
    }

    /**
     * Core method to update student fee payment
     */
    public Student updateFeePayment(Student student, Double paymentAmount, String transactionReference, String source) {
        if (student == null || paymentAmount == null || paymentAmount <= 0) {
            log.warn("Invalid payment update request: student={}, amount={}", student, paymentAmount);
            return student;
        }

        try {
            // Get current fee information
            Double currentPaid = student.getPaidAmount() != null ? student.getPaidAmount() : 0.0;
            Double totalFee = student.getTotalFee() != null ? student.getTotalFee() : 0.0;

            // Update paid amount
            Double newPaidAmount = currentPaid + paymentAmount;
            student.setPaidAmount(newPaidAmount);

            // Calculate new pending amount
            Double pendingAmount = Math.max(0, totalFee - newPaidAmount);
            student.setPendingAmount(pendingAmount);

            // Update fee status
            updateFeeStatus(student, newPaidAmount, totalFee);

            // Update timestamp
            student.setUpdatedAt(LocalDateTime.now());

            // Save and log
            Student savedStudent = studentRepository.save(student);

            log.info("💰 FEE UPDATED: {} received ₹{} via {} (Ref: {})",
                    student.getFullName(),
                    paymentAmount,
                    source,
                    transactionReference);
            log.info("   📊 Total Paid: ₹{} → Pending: ₹{} → Status: {}",
                    newPaidAmount,
                    pendingAmount,
                    student.getFeeStatus());

            return savedStudent;

        } catch (Exception e) {
            log.error("❌ Error updating fee for student {}: {}",
                    student.getFullName(), e.getMessage(), e);
            throw new RuntimeException("Failed to update student fee: " + e.getMessage(), e);
        }
    }

    /**
     * Batch update multiple students from transactions
     */
    public void batchUpdateFeesFromBankTransactions(Map<Student, List<BankTransaction>> studentTransactionsMap) {
        if (studentTransactionsMap == null || studentTransactionsMap.isEmpty()) {
            log.info("No student transactions to update");
            return;
        }

        log.info("🔄 Starting batch fee update for {} students", studentTransactionsMap.size());

        studentTransactionsMap.forEach((student, transactions) -> {
            try {
                Double totalPayment = transactions.stream()
                        .mapToDouble(BankTransaction::getAmount)
                        .sum();

                if (totalPayment > 0) {
                    updateFeePayment(student, totalPayment,
                            "BATCH_" + transactions.get(0).getImportBatchId(),
                            "BATCH_BANK_TX");

                    log.debug("   {}: Applied ₹{} from {} transaction(s)",
                            student.getFullName(), totalPayment, transactions.size());
                }
            } catch (Exception e) {
                log.error("Error in batch update for student {}: {}", student.getFullName(), e.getMessage());
            }
        });

        log.info("✅ Batch fee update completed");
    }

    /**
     * Revert a fee payment (if transaction is deleted or unmatched)
     */
    public Student revertFeePayment(Student student, Double paymentAmount, String reason) {
        if (student == null || paymentAmount == null || paymentAmount <= 0) {
            log.warn("Invalid fee revert request: student={}, amount={}", student, paymentAmount);
            return student;
        }

        try {
            Double currentPaid = student.getPaidAmount() != null ? student.getPaidAmount() : 0.0;
            Double totalFee = student.getTotalFee() != null ? student.getTotalFee() : 0.0;

            // Ensure we don't go negative
            Double newPaidAmount = Math.max(0, currentPaid - paymentAmount);
            student.setPaidAmount(newPaidAmount);

            // Recalculate pending amount
            Double pendingAmount = Math.max(0, totalFee - newPaidAmount);
            student.setPendingAmount(pendingAmount);

            // Update fee status
            updateFeeStatus(student, newPaidAmount, totalFee);

            // Update timestamp
            student.setUpdatedAt(LocalDateTime.now());

            Student savedStudent = studentRepository.save(student);

            log.info("↩️ FEE REVERTED: {} reversed ₹{} (Reason: {})",
                    student.getFullName(),
                    paymentAmount,
                    reason);
            log.info("   📊 Total Paid: ₹{} → Pending: ₹{} → Status: {}",
                    newPaidAmount,
                    pendingAmount,
                    student.getFeeStatus());

            return savedStudent;

        } catch (Exception e) {
            log.error("❌ Error reverting fee for student {}: {}",
                    student.getFullName(), e.getMessage(), e);
            throw new RuntimeException("Failed to revert student fee: " + e.getMessage(), e);
        }
    }

    /**
     * Get student's pending amount with detailed breakdown
     */
    public FeeBreakdown getFeeBreakdown(Student student) {
        if (student == null) {
            return FeeBreakdown.empty();
        }

        Double totalFee = student.getTotalFee() != null ? student.getTotalFee() : 0.0;
        Double paidAmount = student.getPaidAmount() != null ? student.getPaidAmount() : 0.0;
        Double pendingAmount = student.getPendingAmount() != null ? student.getPendingAmount() : 0.0;

        // Recalculate if pending doesn't match
        Double calculatedPending = Math.max(0, totalFee - paidAmount);
        if (!calculatedPending.equals(pendingAmount)) {
            log.warn("⚠️ Pending amount mismatch for {}: DB={}, Calculated={}",
                    student.getFullName(), pendingAmount, calculatedPending);
            pendingAmount = calculatedPending;
        }

        return FeeBreakdown.builder()
                .studentId(student.getId())
                .studentName(student.getFullName())
                .totalFee(totalFee)
                .paidAmount(paidAmount)
                .pendingAmount(pendingAmount)
                .feeStatus(student.getFeeStatus())
                .paymentPercentage(totalFee > 0 ? (paidAmount / totalFee) * 100 : 0.0)
                .build();
    }

    /**
     * Recalculate all students' pending amounts (fix data inconsistencies)
     */
    public void recalculateAllPendingAmounts() {
        log.info("🔧 Recalculating pending amounts for all students...");

        List<Student> allStudents = studentRepository.findAll();
        int updatedCount = 0;

        for (Student student : allStudents) {
            try {
                Double totalFee = student.getTotalFee();
                Double paidAmount = student.getPaidAmount();

                if (totalFee != null && paidAmount != null) {
                    Double calculatedPending = Math.max(0, totalFee - paidAmount);
                    Double currentPending = student.getPendingAmount();

                    // Update if different
                    if (!calculatedPending.equals(currentPending)) {
                        student.setPendingAmount(calculatedPending);
                        updateFeeStatus(student, paidAmount, totalFee);
                        studentRepository.save(student);
                        updatedCount++;

                        log.debug("   Updated {}: Pending ₹{} → ₹{}",
                                student.getFullName(), currentPending, calculatedPending);
                    }
                }
            } catch (Exception e) {
                log.error("Error recalculating for student {}: {}", student.getId(), e.getMessage());
            }
        }

        log.info("✅ Recalculation complete: Updated {}/{} students", updatedCount, allStudents.size());
    }

    /**
     * Helper method to update fee status based on paid amount
     */
    private void updateFeeStatus(Student student, Double paidAmount, Double totalFee) {
        if (paidAmount >= totalFee) {
            student.setFeeStatus(Student.FeeStatus.PAID);
        } else if (paidAmount > 0) {
            // Check if overdue (more than 30 days since last payment/admission)
            if (isPaymentOverdue(student)) {
                student.setFeeStatus(Student.FeeStatus.OVERDUE);
            } else {
                student.setFeeStatus(Student.FeeStatus.PENDING);
            }
        } else {
            student.setFeeStatus(Student.FeeStatus.PENDING);
        }
    }

    /**
     * Check if payment is overdue (more than 30 days)
     */
    private boolean isPaymentOverdue(Student student) {
        // You can customize this logic based on your business rules
        // For now, check if admission was more than 30 days ago
        if (student.getAdmissionDate() != null) {
            return student.getAdmissionDate().isBefore(
                    java.time.LocalDate.now().minusDays(30)
            );
        }
        return false;
    }

    /**
     * DTO for fee breakdown
     */
    @Data
    @Builder
    public static class FeeBreakdown {
        private Long studentId;
        private String studentName;
        private Double totalFee;
        private Double paidAmount;
        private Double pendingAmount;
        private Student.FeeStatus feeStatus;
        private Double paymentPercentage; // 0-100

        public static FeeBreakdown empty() {
            return FeeBreakdown.builder()
                    .totalFee(0.0)
                    .paidAmount(0.0)
                    .pendingAmount(0.0)
                    .paymentPercentage(0.0)
                    .build();
        }

        public String getPaymentProgress() {
            return String.format("%.1f%%", paymentPercentage);
        }
    }
}