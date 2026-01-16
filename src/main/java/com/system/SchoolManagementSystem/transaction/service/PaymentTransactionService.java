package com.system.SchoolManagementSystem.transaction.service;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.transaction.dto.request.PaymentVerificationRequest;
import com.system.SchoolManagementSystem.transaction.entity.BankTransaction;
import com.system.SchoolManagementSystem.transaction.entity.PaymentTransaction;
import com.system.SchoolManagementSystem.transaction.entity.StudentFeeAssignment;
import com.system.SchoolManagementSystem.transaction.enums.TransactionStatus;
import com.system.SchoolManagementSystem.transaction.repository.BankTransactionRepository;
import com.system.SchoolManagementSystem.transaction.repository.PaymentTransactionRepository;
import com.system.SchoolManagementSystem.transaction.repository.StudentFeeAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PaymentTransactionService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final StudentRepository studentRepository;
    private final StudentFeeAssignmentRepository feeAssignmentRepository;

    /**
     * Create payment transaction from matched bank transaction - UPDATED
     * Saves bank transaction first if it's new
     */
    public PaymentTransaction createFromMatchedBankTransaction(BankTransaction bankTransaction) {
        if (bankTransaction == null || bankTransaction.getStudent() == null) {
            throw new RuntimeException("Bank transaction must be matched to a student");
        }

        // Check if payment transaction already exists
        if (bankTransaction.getId() != null && bankTransaction.getPaymentTransaction() != null) {
            log.info("Payment transaction already exists for bank transaction: {}",
                    bankTransaction.getBankReference());
            return bankTransaction.getPaymentTransaction();
        }

        Student student = bankTransaction.getStudent();

        // ========== FIX: Save bank transaction first if it's new ==========
        boolean isNewBankTransaction = bankTransaction.getId() == null;
        BankTransaction savedBankTransaction = bankTransaction;

        if (isNewBankTransaction) {
            // Generate a unique bank reference if not set
            if (bankTransaction.getBankReference() == null || bankTransaction.getBankReference().isEmpty()) {
                bankTransaction.setBankReference("BT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            }

            // Save the bank transaction first
            savedBankTransaction = bankTransactionRepository.save(bankTransaction);
            log.debug("Saved new bank transaction first: ID={}, Ref={}",
                    savedBankTransaction.getId(), savedBankTransaction.getBankReference());
        }

        // Now create the payment transaction with the saved bank transaction
        PaymentTransaction payment = PaymentTransaction.builder()
                .student(student)
                .amount(savedBankTransaction.getAmount())
                .paymentMethod(savedBankTransaction.getPaymentMethod())
                .paymentDate(savedBankTransaction.getTransactionDate().atStartOfDay())
                .bankTransaction(savedBankTransaction) // Use saved transaction
                .bankReference(savedBankTransaction.getBankReference())
                .isVerified(false) // Not verified yet, just matched
                .smsSent(savedBankTransaction.getSmsSent())
                .smsSentAt(savedBankTransaction.getSmsSentAt())
                .smsId(savedBankTransaction.getSmsId())
                .notes("Auto-created from matched bank transaction: " + savedBankTransaction.getDescription())
                .paymentFor("SCHOOL_FEE")
                .discountApplied(0.0)
                .lateFeePaid(0.0)
                .convenienceFee(0.0)
                .build();

        PaymentTransaction saved = paymentTransactionRepository.save(payment);

        // Link back to bank transaction
        savedBankTransaction.setPaymentTransaction(saved);

        // Save again to update the relationship
        if (isNewBankTransaction) {
            bankTransactionRepository.save(savedBankTransaction);
        }

        log.info("✅ Created payment transaction {} from matched bank transaction {} for student {}",
                saved.getReceiptNumber(),
                savedBankTransaction.getBankReference(),
                student.getFullName());

        return saved;
    }

    /**
     * Alternative: Create payment transaction after bank transactions are saved
     * Use this in batch processing
     */
    public PaymentTransaction createFromSavedBankTransaction(Long bankTransactionId) {
        BankTransaction bankTransaction = bankTransactionRepository.findById(bankTransactionId)
                .orElseThrow(() -> new RuntimeException("Bank transaction not found with id: " + bankTransactionId));

        return createFromMatchedBankTransaction(bankTransaction);
    }

    /**
     * Verify a payment transaction (sets isVerified = true)
     */
    public PaymentTransaction verifyPaymentTransaction(Long paymentId) {
        PaymentTransaction payment = getById(paymentId);

        payment.setIsVerified(true);
        payment.setVerifiedAt(LocalDateTime.now());
        // Add verification note
        payment.setNotes((payment.getNotes() != null ? payment.getNotes() + " | " : "") +
                "Verified on: " + LocalDateTime.now());

        PaymentTransaction verified = paymentTransactionRepository.save(payment);

        // Update bank transaction status to VERIFIED
        if (verified.getBankTransaction() != null) {
            BankTransaction bankTransaction = verified.getBankTransaction();
            bankTransaction.setStatus(TransactionStatus.VERIFIED);
            bankTransactionRepository.save(bankTransaction);
        }

        log.info("✅ Verified payment transaction {}", verified.getReceiptNumber());

        return verified;
    }

    /**
     * Get or create payment transaction for bank transaction
     */
    public PaymentTransaction getOrCreateForBankTransaction(BankTransaction bankTransaction) {
        if (bankTransaction == null) {
            throw new RuntimeException("Bank transaction cannot be null");
        }

        // Return existing if found
        if (bankTransaction.getPaymentTransaction() != null) {
            return bankTransaction.getPaymentTransaction();
        }

        // Create new one
        return createFromMatchedBankTransaction(bankTransaction);
    }

    /**
     * Get or create payment transaction for bank transaction ID
     */
    public PaymentTransaction getOrCreateForBankTransactionId(Long bankTransactionId) {
        BankTransaction bankTransaction = bankTransactionRepository.findById(bankTransactionId)
                .orElseThrow(() -> new RuntimeException("Bank transaction not found with id: " + bankTransactionId));

        return getOrCreateForBankTransaction(bankTransaction);
    }

    /**
     * Create payment from verification request (for manual verification)
     */
    public PaymentTransaction createPayment(PaymentVerificationRequest request) {
        log.info("Creating payment for student: {}", request.getStudentId());

        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + request.getStudentId()));

        // Find bank transaction if provided
        BankTransaction bankTransaction = null;
        if (request.getBankTransactionId() != null) {
            bankTransaction = bankTransactionRepository.findById(request.getBankTransactionId())
                    .orElseThrow(() -> new RuntimeException("Bank transaction not found with id: " + request.getBankTransactionId()));
        }

        // Find fee assignment if provided
        StudentFeeAssignment feeAssignment = null;
        if (request.getFeeAssignmentId() != null) {
            feeAssignment = feeAssignmentRepository.findById(request.getFeeAssignmentId())
                    .orElseThrow(() -> new RuntimeException("Fee assignment not found with id: " + request.getFeeAssignmentId()));
        }

        PaymentTransaction payment = PaymentTransaction.builder()
                .student(student)
                .feeAssignment(feeAssignment)
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .paymentDate(request.getPaymentDate() != null ?
                        request.getPaymentDate().atStartOfDay() : LocalDateTime.now())
                .bankTransaction(bankTransaction)
                .bankReference(bankTransaction != null ? bankTransaction.getBankReference() : null)
                .isVerified(request.getBankTransactionId() != null) // Auto-verify if linked to bank transaction
                .paymentFor(request.getPaymentFor())
                .discountApplied(request.getDiscountApplied())
                .lateFeePaid(request.getLateFeePaid())
                .convenienceFee(request.getConvenienceFee())
                .notes(request.getNotes())
                .build();

        // If linked to bank transaction, update bank transaction status
        if (bankTransaction != null) {
            if (payment.getIsVerified()) {
                bankTransaction.setStatus(TransactionStatus.VERIFIED);
            } else {
                bankTransaction.setStatus(TransactionStatus.MATCHED);
            }
            bankTransaction.setStudent(student);
            bankTransaction.setPaymentTransaction(payment);
            bankTransactionRepository.save(bankTransaction);
        }

        PaymentTransaction saved = paymentTransactionRepository.save(payment);

        if (bankTransaction != null) {
            log.info("✅ Created payment transaction {} from bank transaction {}",
                    saved.getReceiptNumber(), bankTransaction.getBankReference());
        } else {
            log.info("✅ Created manual payment transaction {}",
                    saved.getReceiptNumber());
        }

        return saved;
    }

    /**
     * Legacy method - kept for backward compatibility
     */
    public PaymentTransaction verifyPayment(Long paymentId, Long verifiedByUserId) {
        return verifyPaymentTransaction(paymentId);
    }

    public PaymentTransaction updatePayment(Long paymentId, PaymentVerificationRequest request) {
        PaymentTransaction payment = getById(paymentId);

        if (request.getAmount() != null) {
            payment.setAmount(request.getAmount());
        }

        if (request.getPaymentMethod() != null) {
            payment.setPaymentMethod(request.getPaymentMethod());
        }

        if (request.getPaymentDate() != null) {
            payment.setPaymentDate(request.getPaymentDate().atStartOfDay());
        }

        if (request.getNotes() != null) {
            payment.setNotes(request.getNotes());
        }

        payment.setPaymentFor(request.getPaymentFor());
        payment.setDiscountApplied(request.getDiscountApplied());
        payment.setLateFeePaid(request.getLateFeePaid());
        payment.setConvenienceFee(request.getConvenienceFee());

        return paymentTransactionRepository.save(payment);
    }

    public PaymentTransaction getById(Long id) {
        return paymentTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment transaction not found with id: " + id));
    }

    public PaymentTransaction getByReceiptNumber(String receiptNumber) {
        return paymentTransactionRepository.findByReceiptNumber(receiptNumber)
                .orElseThrow(() -> new RuntimeException("Payment transaction not found with receipt number: " + receiptNumber));
    }

    public Page<PaymentTransaction> getAllPayments(Pageable pageable) {
        return paymentTransactionRepository.findAll(pageable);
    }

    public Page<PaymentTransaction> getVerifiedPayments(Pageable pageable) {
        return paymentTransactionRepository.findByIsVerified(true, pageable);
    }

    public List<PaymentTransaction> getByStudentId(Long studentId) {
        return paymentTransactionRepository.findByStudentId(studentId);
    }

    public List<PaymentTransaction> getByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return paymentTransactionRepository.findByPaymentDateBetween(startDate, endDate);
    }

    public Double getTotalVerifiedAmount() {
        Double total = paymentTransactionRepository.getTotalVerifiedAmount();
        return total != null ? total : 0.0;
    }

    public Double getTotalVerifiedAmountToday() {
        Double todayTotal = paymentTransactionRepository.getTotalVerifiedAmountToday();
        return todayTotal != null ? todayTotal : 0.0;
    }

    public Double getTotalAmountByStudent(Long studentId) {
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByStudentIdAndIsVerifiedTrue(studentId);
        return transactions.stream()
                .mapToDouble(PaymentTransaction::getAmount)
                .sum();
    }

    public void deletePayment(Long id) {
        if (!paymentTransactionRepository.existsById(id)) {
            throw new RuntimeException("Payment transaction not found with id: " + id);
        }
        paymentTransactionRepository.deleteById(id);
        log.info("Deleted payment transaction with id: {}", id);
    }

    public PaymentTransaction linkToBankTransaction(Long paymentId, Long bankTransactionId) {
        PaymentTransaction payment = getById(paymentId);
        BankTransaction bankTransaction = bankTransactionRepository.findById(bankTransactionId)
                .orElseThrow(() -> new RuntimeException("Bank transaction not found with id: " + bankTransactionId));

        payment.setBankTransaction(bankTransaction);
        payment.setBankReference(bankTransaction.getBankReference());

        // Update bank transaction
        bankTransaction.setPaymentTransaction(payment);
        bankTransaction.setStudent(payment.getStudent());
        bankTransaction.setStatus(TransactionStatus.MATCHED);
        bankTransactionRepository.save(bankTransaction);

        return paymentTransactionRepository.save(payment);
    }

    public PaymentTransaction unlinkBankTransaction(Long paymentId) {
        PaymentTransaction payment = getById(paymentId);

        if (payment.getBankTransaction() != null) {
            BankTransaction bankTransaction = payment.getBankTransaction();
            bankTransaction.setPaymentTransaction(null);
            bankTransaction.setStatus(TransactionStatus.UNVERIFIED);
            bankTransactionRepository.save(bankTransaction);
        }

        payment.setBankTransaction(null);
        payment.setBankReference(null);

        return paymentTransactionRepository.save(payment);
    }

    // ========== NEW: Batch processing method ==========

    /**
     * Process matched transactions after they've been saved
     * Use this in the TransactionService after saveTransactionsInBatches()
     */
    public void createPaymentTransactionsForMatchedTransactions(List<BankTransaction> savedTransactions) {
        if (savedTransactions == null || savedTransactions.isEmpty()) {
            return;
        }

        log.info("Creating payment transactions for {} saved bank transactions", savedTransactions.size());

        int createdCount = 0;
        for (BankTransaction bankTransaction : savedTransactions) {
            try {
                // Only create payment transactions for matched transactions
                if (bankTransaction.getStudent() != null &&
                        bankTransaction.getStatus() == TransactionStatus.MATCHED &&
                        bankTransaction.getPaymentTransaction() == null) {

                    createFromMatchedBankTransaction(bankTransaction);
                    createdCount++;
                }
            } catch (Exception e) {
                log.warn("Failed to create payment transaction for bank transaction {}: {}",
                        bankTransaction.getBankReference(), e.getMessage());
            }
        }

        log.info("✅ Created {} payment transactions for matched bank transactions", createdCount);
    }
}