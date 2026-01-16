package com.system.SchoolManagementSystem.transaction.service;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.transaction.dto.request.*;
import com.system.SchoolManagementSystem.transaction.dto.response.*;
import com.system.SchoolManagementSystem.transaction.enums.TransactionStatus;
import com.system.SchoolManagementSystem.transaction.entity.*;
import com.system.SchoolManagementSystem.transaction.repository.*;
import com.system.SchoolManagementSystem.transaction.util.*;
import com.system.SchoolManagementSystem.auth.entity.User;
import com.system.SchoolManagementSystem.auth.repository.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    // ========== REPOSITORIES ==========
    private final BankTransactionRepository bankTransactionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final StudentFeeAssignmentRepository feeAssignmentRepository;
    private final FeeInstallmentRepository feeInstallmentRepository;
    private final SmsLogRepository smsLogRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    // ========== UTILITIES ==========
    private final ReceiptGenerator receiptGenerator;
    private final BankStatementParser bankStatementParser;

    // ========== NEW OPTIMIZATION COMPONENTS ==========
    private final TransactionMatcher transactionMatcher;
    private final StudentCacheService studentCacheService;
    private final ImportProgressTracker importProgressTracker;
    private final StudentFeeUpdateService studentFeeUpdateService;
    private final PaymentTransactionService paymentTransactionService;

    // ========== PERFORMANCE MONITORING ==========
    private final Map<String, ImportProgress> importProgressMap = new ConcurrentHashMap<>();

    // ========== INITIALIZATION ==========

    @PostConstruct
    public void initialize() {
        log.info("Initializing TransactionService with optimization components...");

        // Initialize transaction matcher cache asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Loading students for transaction matcher cache...");
                List<Student> allStudents = studentRepository.findAll();
                transactionMatcher.initializeCache(allStudents);
                log.info("✅ Transaction matcher cache initialized with {} students", allStudents.size());
            } catch (Exception e) {
                log.error("Failed to initialize transaction matcher cache", e);
            }
        });
    }

    // ========== BANK TRANSACTION OPERATIONS ==========

    /**
     * Original synchronous import (optimized)
     */
    public List<BankTransactionResponse> importBankTransactions(BankTransactionImportRequest request) {
        log.info("📥 Importing bank transactions synchronously: {}",
                request.getFile().getOriginalFilename());

        List<BankTransaction> transactions;
        String fileType = request.getFile().getContentType();

        try {
            if (fileType != null && fileType.contains("csv")) {
                transactions = bankStatementParser.parseCsv(request.getFile(), request.getBankAccount());
            } else if (fileType != null && (fileType.contains("excel") || fileType.contains("spreadsheet"))) {
                transactions = bankStatementParser.parseExcel(request.getFile(), request.getBankAccount());
            } else {
                throw new IllegalArgumentException("Unsupported file type: " + fileType);
            }

            // Use optimized auto-matching with cache
            List<BankTransaction> matchedTransactions = autoMatchTransactionsOptimized(transactions);

            // Save transactions in batches for performance
            List<BankTransaction> savedTransactions = saveTransactionsInBatches(matchedTransactions);

            long autoMatchedCount = savedTransactions.stream()
                    .filter(bt -> bt.getStudent() != null)
                    .count();

            log.info("✅ Synchronous import completed: {} transactions, {} auto-matched",
                    savedTransactions.size(), autoMatchedCount);

            // Convert to response DTOs
            return savedTransactions.stream()
                    .map(this::convertToBankTransactionResponse)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Failed to import bank transactions", e);
            throw new RuntimeException("Failed to import transactions: " + e.getMessage());
        }
    }

    /**
     * FIXED: OPTIMIZED Auto-matching using cache properly - O(n) complexity
     */
    /**
     * FIXED: Auto-matching with proper cache usage
     */
    private List<BankTransaction> autoMatchTransactionsOptimized(List<BankTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            log.info("No transactions to match");
            return Collections.emptyList();
        }

        log.info("🚀 Starting OPTIMIZED auto-matching for {} transactions", transactions.size());
        long startTime = System.currentTimeMillis();

        // ========== Load students ONCE for cache initialization ==========
        List<Student> allStudents = null;

        if (!transactionMatcher.isCacheLoaded()) {
            log.info("Cache not loaded, loading {} students...");
            allStudents = studentRepository.findAll();
            transactionMatcher.initializeCache(allStudents);
            log.info("✅ Cache initialized with {} students", allStudents.size());
        } else {
            log.info("✅ Using pre-loaded cache");
        }

        List<BankTransaction> matchedTransactions = new ArrayList<>(transactions.size());
        AtomicInteger autoMatchedCount = new AtomicInteger(0);

        // ========== CRITICAL: Pass EMPTY list to force cache usage ==========
        for (BankTransaction transaction : transactions) {
            // Pass empty list - matcher will use cache (O(1) operations)
            Optional<Student> matchedStudent = transactionMatcher.findMatchingStudent(
                    transaction, Collections.emptyList());

            if (matchedStudent.isPresent()) {
                Student student = matchedStudent.get();
                String description = transaction.getDescription().toLowerCase();
                String studentName = student.getFullName().toLowerCase();

                // Validate: description must contain student name
                if (description.contains(studentName)) {
                    transaction.setStudent(student);
                    transaction.setStatus(TransactionStatus.MATCHED);
                    transaction.setMatchedAt(LocalDateTime.now());

                    autoMatchedCount.incrementAndGet();

                    log.debug("✅ Auto-matched via cache: '{}' → '{}'",
                            transaction.getDescription(), student.getFullName());
                } else {
                    log.warn("⚠️ Cache returned invalid match: '{}' doesn't contain '{}'",
                            description, studentName);
                }
            }

            matchedTransactions.add(transaction);
        }

        long duration = System.currentTimeMillis() - startTime;
        double rate = transactions.size() / (duration / 1000.0);

        log.info("📊 Auto-matching completed in {}ms: {}/{} matched ({:.1f} txn/sec)",
                duration, autoMatchedCount.get(), transactions.size(), rate);

        return matchedTransactions;
    }

    /**
     * Helper method to ensure cache is loaded
     */
    private void ensureCacheLoaded() {
        if (!transactionMatcher.isCacheLoaded()) {
            log.warn("Transaction matcher cache not loaded, loading now...");
            List<Student> allStudents = studentRepository.findAll();
            transactionMatcher.initializeCache(allStudents);
            log.info("✅ Cache loaded with {} students", allStudents.size());
        }
    }

    /**
     * Save transactions in batches for performance - UPDATED (Creates payment transactions after saving)
     */
    private List<BankTransaction> saveTransactionsInBatches(List<BankTransaction> transactions) {
        if (transactions.isEmpty()) {
            return Collections.emptyList();
        }

        List<BankTransaction> savedTransactions = new ArrayList<>();
        int batchSize = 1000;
        int totalBatches = (transactions.size() + batchSize - 1) / batchSize;

        for (int i = 0; i < transactions.size(); i += batchSize) {
            int end = Math.min(i + batchSize, transactions.size());
            List<BankTransaction> batch = transactions.subList(i, end);

            try {
                // Save bank transactions first
                List<BankTransaction> savedBatch = bankTransactionRepository.saveAll(batch);
                savedTransactions.addAll(savedBatch);

                // Now create payment transactions for matched ones
                int paymentCreatedCount = 0;
                for (BankTransaction savedTransaction : savedBatch) {
                    if (savedTransaction.getStudent() != null &&
                            savedTransaction.getStatus() == TransactionStatus.MATCHED &&
                            savedTransaction.getPaymentTransaction() == null) {

                        try {
                            PaymentTransaction paymentTransaction =
                                    paymentTransactionService.createFromMatchedBankTransaction(savedTransaction);

                            // Update student fees
                            studentFeeUpdateService.updateFeeFromPaymentTransaction(
                                    savedTransaction.getStudent(),
                                    paymentTransaction
                            );

                            // Send SMS
                            sendAutoMatchSms(
                                    savedTransaction.getStudent(),
                                    savedTransaction,
                                    paymentTransaction
                            );

                            paymentCreatedCount++;

                            log.trace("Created payment {} for bank transaction {}",
                                    paymentTransaction.getReceiptNumber(),
                                    savedTransaction.getBankReference());

                        } catch (Exception e) {
                            log.warn("Failed to create payment for transaction {}: {}",
                                    savedTransaction.getBankReference(), e.getMessage());
                        }
                    }
                }

                if (i % (batchSize * 5) == 0) {
                    log.debug("Saved batch {}/{}: {} transactions, {} payment transactions created",
                            (i / batchSize) + 1, totalBatches, savedBatch.size(), paymentCreatedCount);
                }

            } catch (Exception e) {
                log.error("Failed to save batch {}-{}: {}", i, end, e.getMessage());
            }
        }

        log.info("✅ Saved {} transactions and created payment transactions for matched ones", savedTransactions.size());
        return savedTransactions;
    }

    // ========== UPDATED AUTO-MATCH SMS METHOD ==========

    @Async
    private void sendAutoMatchSms(Student student, BankTransaction transaction, PaymentTransaction paymentTransaction) {
        try {
            log.info("📱 Attempting to send auto-match SMS for payment: {}",
                    paymentTransaction.getReceiptNumber());

            // Get recipient phone
            String recipientPhone = getBestContactPhone(student);
            if (recipientPhone == null || recipientPhone.trim().isEmpty()) {
                log.warn("📵 No valid phone number available for student {} to send auto-match SMS",
                        student.getFullName());
                return;
            }

            // Clean and validate phone number
            recipientPhone = cleanPhoneNumber(recipientPhone);
            if (!isValidIndianPhoneNumber(recipientPhone)) {
                log.warn("📵 Invalid phone number format for student {}: {}", student.getFullName(), recipientPhone);
                return;
            }

            // Create SMS message for auto-matched transaction WITH RECEIPT NUMBER
            String message = String.format(
                    "Dear Parent/Guardian,\n" +
                            "Payment of ₹%.2f has been auto-matched to %s (Class: %s).\n" +
                            "Receipt: %s | Bank Ref: %s\n" +
                            "Transaction Date: %s\n" +
                            "Thank you! - School Management System",
                    transaction.getAmount(),
                    student.getFullName(),
                    student.getGrade(),
                    paymentTransaction.getReceiptNumber(),
                    transaction.getBankReference(),
                    transaction.getTransactionDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            );

            // Create SMS log
            SmsLog smsLog = SmsLog.builder()
                    .student(student)
                    .paymentTransaction(paymentTransaction) // Link to payment transaction
                    .recipientPhone(recipientPhone)
                    .message(message)
                    .status(SmsLog.SmsStatus.SENT)
                    .gatewayMessageId("AUTO-" + UUID.randomUUID().toString().substring(0, 8))
                    .gatewayResponse("Auto-match SMS sent successfully")
                    .sentAt(LocalDateTime.now())
                    .build();

            SmsLog savedSmsLog = smsLogRepository.save(smsLog);

            // Update bank transaction with SMS info
            transaction.setSmsSent(true);
            transaction.setSmsSentAt(LocalDateTime.now());
            transaction.setSmsId(savedSmsLog.getGatewayMessageId());
            bankTransactionRepository.save(transaction);

            // Update payment transaction with SMS info
            paymentTransaction.setSmsSent(true);
            paymentTransaction.setSmsSentAt(LocalDateTime.now());
            paymentTransaction.setSmsId(savedSmsLog.getGatewayMessageId());
            paymentTransactionRepository.save(paymentTransaction);

            log.info("✅ Auto-match SMS sent to {} for student {} (Receipt: {})",
                    recipientPhone, student.getFullName(), paymentTransaction.getReceiptNumber());

        } catch (Exception e) {
            log.warn("⚠️ Failed to send auto-match SMS for transaction {}: {}",
                    transaction.getBankReference(), e.getMessage());
        }
    }

    // ========== EXISTING METHODS (UPDATED FOR PAYMENT TRANSACTION CREATION) ==========

    public Page<BankTransactionResponse> getBankTransactions(TransactionStatus status, String search, Pageable pageable) {
        try {
            Page<BankTransaction> transactions;

            if (status != null && search != null && !search.trim().isEmpty()) {
                // Use repository search method if available
                transactions = bankTransactionRepository.searchTransactions(search, pageable);
                // Filter by status in memory for small result sets
                List<BankTransaction> filtered = transactions.getContent().stream()
                        .filter(t -> t.getStatus() == status)
                        .collect(Collectors.toList());

                return new PageImpl<>(filtered.stream()
                        .map(this::convertToBankTransactionResponse)
                        .collect(Collectors.toList()),
                        pageable,
                        filtered.size());

            } else if (status != null) {
                transactions = bankTransactionRepository.findByStatus(status, pageable);
            } else if (search != null && !search.trim().isEmpty()) {
                transactions = bankTransactionRepository.searchTransactions(search, pageable);
            } else {
                transactions = bankTransactionRepository.findAll(pageable);
            }

            return transactions.map(this::convertToBankTransactionResponse);

        } catch (Exception e) {
            log.error("❌ Error getting bank transactions", e);
            // Return empty page instead of throwing
            return Page.empty(pageable);
        }
    }

    public BankTransactionResponse getBankTransactionById(Long id) {
        BankTransaction transaction = bankTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bank transaction not found with id: " + id));
        return convertToBankTransactionResponse(transaction);
    }

    public BankTransactionResponse matchBankTransaction(Long transactionId, Long studentId) {
        BankTransaction transaction = bankTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // Update transaction with student info
        transaction.setStudent(student);
        transaction.setStatus(TransactionStatus.MATCHED);
        transaction.setMatchedAt(LocalDateTime.now());

        // ========== UPDATED: Create payment transaction (bank transaction already saved) ==========
        try {
            PaymentTransaction paymentTransaction =
                    paymentTransactionService.createFromMatchedBankTransaction(transaction);

            // Update student fees
            studentFeeUpdateService.updateFeeFromPaymentTransaction(student, paymentTransaction);
            log.info("💰 Student fees updated via manual match: {} +₹{} (Receipt: {})",
                    student.getFullName(), transaction.getAmount(),
                    paymentTransaction.getReceiptNumber());

        } catch (Exception feeError) {
            log.error("⚠️ Failed to create payment during manual match: {}", feeError.getMessage());
            // Revert
            transaction.setStudent(null);
            transaction.setStatus(TransactionStatus.UNVERIFIED);
            transaction.setPaymentTransaction(null);
            throw new RuntimeException("Failed to create payment transaction: " + feeError.getMessage());
        }

        BankTransaction savedTransaction = bankTransactionRepository.save(transaction);

        // Send auto-match SMS for manual matches too
        if (savedTransaction.getPaymentTransaction() != null) {
            sendAutoMatchSms(student, savedTransaction, savedTransaction.getPaymentTransaction());
        }

        log.info("✅ Manually matched transaction {} to student {}", transactionId, student.getFullName());
        return convertToBankTransactionResponse(savedTransaction);
    }

    public void deleteBankTransaction(Long id) {
        BankTransaction transaction = bankTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        // Check if payment transaction exists
        PaymentTransaction paymentTransaction = transaction.getPaymentTransaction();

        // If transaction was matched to a student, revert the fee payment
        if (transaction.getStudent() != null && transaction.getAmount() != null) {
            try {
                studentFeeUpdateService.revertFeePayment(
                        transaction.getStudent(),
                        transaction.getAmount(),
                        "Transaction deleted: " + transaction.getBankReference()
                );
                log.info("↩️ Reverted fee payment for student {}: -₹{}",
                        transaction.getStudent().getFullName(), transaction.getAmount());
            } catch (Exception feeError) {
                log.error("⚠️ Failed to revert fee payment: {}", feeError.getMessage());
                // Continue with deletion anyway
            }
        }

        // Delete payment transaction first (if exists)
        if (paymentTransaction != null) {
            paymentTransactionRepository.delete(paymentTransaction);
            log.info("🗑️ Deleted linked payment transaction: {}", paymentTransaction.getReceiptNumber());
        }

        // Delete bank transaction
        bankTransactionRepository.delete(transaction);
        log.info("🗑️ Deleted bank transaction with id: {}", id);
    }

    // ========== PAYMENT TRANSACTION OPERATIONS ==========

    public PaymentTransactionResponse verifyPayment(PaymentVerificationRequest request) {
        log.info("🔐 Verifying payment for bank transaction: {}", request.getBankTransactionId());

        // Find bank transaction
        BankTransaction bankTransaction = bankTransactionRepository.findById(request.getBankTransactionId())
                .orElseThrow(() -> new RuntimeException("Bank transaction not found"));

        // Find student
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // ========== UPDATED: Use bank transaction ID method ==========
        PaymentTransaction paymentTransaction =
                paymentTransactionService.getOrCreateForBankTransactionId(request.getBankTransactionId());

        // If payment wasn't verified before, update it with verification details
        if (!paymentTransaction.getIsVerified()) {
            paymentTransaction.setAmount(request.getAmount());
            paymentTransaction.setPaymentMethod(request.getPaymentMethod());
            paymentTransaction.setPaymentDate(LocalDateTime.now());
            paymentTransaction.setPaymentFor(request.getPaymentFor());
            paymentTransaction.setDiscountApplied(request.getDiscountApplied());
            paymentTransaction.setLateFeePaid(request.getLateFeePaid());
            paymentTransaction.setConvenienceFee(request.getConvenienceFee());
            paymentTransaction.setNotes(request.getNotes());
            paymentTransaction.setIsVerified(true);
            paymentTransaction.setVerifiedAt(LocalDateTime.now());

            // Update bank transaction
            bankTransaction.setStudent(student);
            bankTransaction.setStatus(TransactionStatus.VERIFIED);
            bankTransactionRepository.save(bankTransaction);

            paymentTransactionRepository.save(paymentTransaction);
        }

        // Verify the payment transaction
        PaymentTransaction verifiedTransaction =
                paymentTransactionService.verifyPaymentTransaction(paymentTransaction.getId());

        // Update student fees
        try {
            studentFeeUpdateService.updateFeeFromPaymentTransaction(student, verifiedTransaction);
            log.info("💰 Student fees updated via payment verification: {} +₹{} (Receipt: {})",
                    student.getFullName(), request.getAmount(),
                    verifiedTransaction.getReceiptNumber());
        } catch (Exception feeError) {
            log.error("⚠️ Failed to update student fees during verification: {}", feeError.getMessage());
        }

        log.info("✅ Payment verified: Receipt {} for student {} - ₹{}",
                verifiedTransaction.getReceiptNumber(),
                student.getFullName(),
                verifiedTransaction.getAmount());

        // Send SMS if requested
        if (request.getSendSms() != null && request.getSendSms()) {
            sendPaymentSmsAsync(student, verifiedTransaction, request.getAmount());
        }

        return convertToPaymentTransactionResponse(verifiedTransaction);
    }

    @Async
    protected void sendPaymentSmsAsync(Student student, PaymentTransaction transaction, Double amount) {
        try {
            SmsRequest smsRequest = new SmsRequest();
            smsRequest.setStudentId(student.getId());
            smsRequest.setPaymentTransactionId(transaction.getId());
            smsRequest.setMessage("Payment of ₹" + amount + " received. Receipt: " + transaction.getReceiptNumber());

            // Get student phone or emergency contact phone
            String recipientPhone = getBestContactPhone(student);
            if (recipientPhone != null && !recipientPhone.trim().isEmpty()) {
                smsRequest.setRecipientPhone(recipientPhone);
                sendPaymentSms(smsRequest);
                log.info("📱 SMS sent for payment {}", transaction.getReceiptNumber());
            } else {
                log.warn("📵 No phone number available for student {} to send SMS", student.getFullName());
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to send SMS for payment {}: {}", transaction.getId(), e.getMessage());
        }
    }

    public List<PaymentTransactionResponse> bulkVerifyPayments(BulkVerificationRequest request) {
        log.info("📦 Bulk verifying {} payments", request.getBankTransactionIds().size());

        List<PaymentTransactionResponse> responses = new ArrayList<>();

        for (Long bankTransactionId : request.getBankTransactionIds()) {
            try {
                BankTransaction bankTransaction = bankTransactionRepository.findById(bankTransactionId)
                        .orElseThrow(() -> new RuntimeException("Bank transaction not found: " + bankTransactionId));

                // Check if transaction is already matched to a student
                if (bankTransaction.getStudent() == null) {
                    log.warn("⚠️ Skipping transaction {} - not matched to any student", bankTransactionId);
                    continue;
                }

                // ========== UPDATED: Use bank transaction ID method ==========
                PaymentTransaction paymentTransaction =
                        paymentTransactionService.getOrCreateForBankTransactionId(bankTransactionId);

                // Create payment verification request
                PaymentVerificationRequest singleRequest = new PaymentVerificationRequest();
                singleRequest.setBankTransactionId(bankTransactionId);
                singleRequest.setStudentId(bankTransaction.getStudent().getId());
                singleRequest.setAmount(bankTransaction.getAmount());
                singleRequest.setPaymentMethod(bankTransaction.getPaymentMethod());
                singleRequest.setSendSms(request.getSendSms());
                singleRequest.setNotes(request.getNotes());
                singleRequest.setPaymentFor("SCHOOL_FEE");

                // Verify the payment
                PaymentTransactionResponse verifiedPayment = verifyPayment(singleRequest);
                responses.add(verifiedPayment);

                log.info("✅ Bulk verified transaction {} for student {} (Receipt: {})",
                        bankTransactionId,
                        bankTransaction.getStudent().getFullName(),
                        paymentTransaction.getReceiptNumber());

            } catch (Exception e) {
                log.error("❌ Failed to verify payment for transaction {}: {}", bankTransactionId, e.getMessage());
            }
        }

        log.info("📊 Bulk verification completed: {} successful, {} total",
                responses.size(), request.getBankTransactionIds().size());
        return responses;
    }

    public Page<PaymentTransactionResponse> getVerifiedTransactions(String search, Pageable pageable) {
        try {
            Page<PaymentTransaction> transactions;

            if (search != null && !search.trim().isEmpty()) {
                transactions = paymentTransactionRepository.searchTransactions(search, pageable);

                // Filter to only verified transactions
                List<PaymentTransaction> verifiedList = transactions.getContent().stream()
                        .filter(PaymentTransaction::getIsVerified)
                        .collect(Collectors.toList());

                // Create a new page with verified transactions
                Page<PaymentTransaction> verifiedPage = new PageImpl<>(
                        verifiedList,
                        pageable,
                        verifiedList.size()
                );

                return verifiedPage.map(this::convertToPaymentTransactionResponse);

            } else {
                // Use the repository method for verified transactions
                transactions = paymentTransactionRepository.findByIsVerified(true, pageable);
                return transactions.map(this::convertToPaymentTransactionResponse);
            }

        } catch (Exception e) {
            log.error("❌ Error getting verified transactions", e);
            // Return empty page instead of throwing
            return Page.empty(pageable);
        }
    }

    public PaymentTransactionResponse getPaymentTransactionById(Long id) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment transaction not found with id: " + id));
        return convertToPaymentTransactionResponse(transaction);
    }

    public List<PaymentTransactionResponse> getStudentTransactions(Long studentId) {
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByStudentId(studentId);
        return transactions.stream()
                .map(this::convertToPaymentTransactionResponse)
                .collect(Collectors.toList());
    }

    // ========== STATISTICS OPERATIONS ==========

    public TransactionStatisticsResponse getTransactionStatistics() {
        TransactionStatisticsResponse statistics = new TransactionStatisticsResponse();

        try {
            // Get counts using repository methods (optimized)
            long totalBank = bankTransactionRepository.count();
            long unverifiedCount = bankTransactionRepository.countByStatus(TransactionStatus.UNVERIFIED);
            long matchedCount = bankTransactionRepository.countByStatus(TransactionStatus.MATCHED);
            long verifiedCount = paymentTransactionRepository.countByIsVerified(true);

            // Set counts
            statistics.setUnverifiedCount(unverifiedCount);
            statistics.setMatchedCount(matchedCount);
            statistics.setVerifiedCount(verifiedCount);

            // Calculate match rate
            if (totalBank > 0) {
                double matchRate = (matchedCount * 100.0) / totalBank;
                statistics.setMatchRate(String.format("%.1f%%", matchRate));
                log.info("📈 MATCH RATE: {}/{} = {}%", matchedCount, totalBank, matchRate);
            } else {
                statistics.setMatchRate("0%");
            }

            // Calculate total verified amount from payment transactions
            Double totalAmount = paymentTransactionRepository.getTotalVerifiedAmount();
            statistics.setTotalAmount(totalAmount != null ? totalAmount : 0.0);

            // Calculate today's verified amount
            Double todayAmount = paymentTransactionRepository.getTotalVerifiedAmountToday();
            statistics.setTodayAmount(todayAmount != null ? todayAmount : 0.0);

            // Get pending and overdue payments from students
            List<Student> studentsWithPending = studentRepository.findAll().stream()
                    .filter(s -> s.getFeeStatus() == Student.FeeStatus.PENDING ||
                            s.getFeeStatus() == Student.FeeStatus.OVERDUE)
                    .collect(Collectors.toList());

            long pendingPayments = studentsWithPending.stream()
                    .filter(s -> s.getFeeStatus() == Student.FeeStatus.PENDING)
                    .count();

            long overduePayments = studentsWithPending.stream()
                    .filter(s -> s.getFeeStatus() == Student.FeeStatus.OVERDUE)
                    .count();

            statistics.setPendingPayments(pendingPayments);
            statistics.setOverduePayments(overduePayments);

            // Calculate total pending amount
            double totalPendingAmount = studentsWithPending.stream()
                    .mapToDouble(s -> s.getPendingAmount() != null ? s.getPendingAmount() : 0.0)
                    .sum();
            statistics.setTotalPendingAmount(totalPendingAmount);

            log.info("📊 Statistics calculated: Match Rate={}, Pending Students={}, Total Pending=₹{}",
                    statistics.getMatchRate(), pendingPayments, totalPendingAmount);

        } catch (Exception e) {
            log.error("❌ Error calculating transaction statistics", e);
            statistics.setMatchRate("Error");
        }

        return statistics;
    }

    public TransactionStatisticsResponse getStatisticsByDateRange(LocalDate startDate, LocalDate endDate) {
        TransactionStatisticsResponse statistics = new TransactionStatisticsResponse();

        try {
            // Count unverified bank transactions in date range
            List<BankTransaction> unverifiedTransactions = bankTransactionRepository
                    .findByTransactionDateBetween(startDate, endDate)
                    .stream()
                    .filter(t -> t.getStatus() == TransactionStatus.UNVERIFIED)
                    .collect(Collectors.toList());
            statistics.setUnverifiedCount((long) unverifiedTransactions.size());

            // Count verified payments in date range
            List<PaymentTransaction> verifiedPayments = paymentTransactionRepository
                    .findByPaymentDateBetween(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay())
                    .stream()
                    .filter(PaymentTransaction::getIsVerified)
                    .collect(Collectors.toList());
            statistics.setVerifiedCount((long) verifiedPayments.size());

            // Calculate total amount in date range
            Double totalAmount = verifiedPayments.stream()
                    .mapToDouble(PaymentTransaction::getAmount)
                    .sum();
            statistics.setTotalAmount(totalAmount);

            // Calculate today's amount (if today is within range)
            if (LocalDate.now().isAfter(startDate.minusDays(1)) && LocalDate.now().isBefore(endDate.plusDays(1))) {
                Double todayAmount = paymentTransactionRepository.getTotalVerifiedAmountToday();
                statistics.setTodayAmount(todayAmount != null ? todayAmount : 0.0);
            } else {
                statistics.setTodayAmount(0.0);
            }

            // Calculate match rate
            long totalProcessed = statistics.getVerifiedCount();
            long totalTransactions = statistics.getUnverifiedCount() + totalProcessed;

            if (totalTransactions > 0) {
                double matchRate = (totalProcessed * 100.0) / totalTransactions;
                statistics.setMatchRate(String.format("%.1f%%", matchRate));
            } else {
                statistics.setMatchRate("0%");
            }

        } catch (Exception e) {
            log.error("❌ Error calculating date range statistics", e);
        }

        return statistics;
    }

    // ========== SMS OPERATIONS ==========

    public SmsLogResponse sendPaymentSms(SmsRequest request) {
        log.info("📱 Sending SMS for payment transaction: {}", request.getPaymentTransactionId());

        PaymentTransaction paymentTransaction = paymentTransactionRepository.findById(request.getPaymentTransactionId())
                .orElseThrow(() -> new RuntimeException("Payment transaction not found"));

        Student student = paymentTransaction.getStudent();

        // Get recipient phone - use request phone or fallback to best available contact
        String recipientPhone = request.getRecipientPhone();
        if (recipientPhone == null || recipientPhone.trim().isEmpty()) {
            recipientPhone = getBestContactPhone(student);
        }

        // Validate phone number
        if (recipientPhone == null || recipientPhone.trim().isEmpty()) {
            log.warn("📵 No valid phone number available for student {}", student.getFullName());

            // Create failed SMS log for tracking
            SmsLog smsLog = SmsLog.builder()
                    .student(student)
                    .paymentTransaction(paymentTransaction)
                    .recipientPhone("N/A")
                    .message(request.getMessage())
                    .status(SmsLog.SmsStatus.FAILED)
                    .gatewayResponse("No valid phone number available")
                    .sentAt(LocalDateTime.now())
                    .build();

            SmsLog savedSmsLog = smsLogRepository.save(smsLog);
            return convertToSmsLogResponse(savedSmsLog);
        }

        // Clean and validate phone number
        recipientPhone = cleanPhoneNumber(recipientPhone);
        if (!isValidIndianPhoneNumber(recipientPhone)) {
            log.warn("📵 Invalid phone number format for student {}: {}", student.getFullName(), recipientPhone);

            SmsLog smsLog = SmsLog.builder()
                    .student(student)
                    .paymentTransaction(paymentTransaction)
                    .recipientPhone(recipientPhone)
                    .message(request.getMessage())
                    .status(SmsLog.SmsStatus.FAILED)
                    .gatewayResponse("Invalid phone number format")
                    .sentAt(LocalDateTime.now())
                    .build();

            SmsLog savedSmsLog = smsLogRepository.save(smsLog);
            return convertToSmsLogResponse(savedSmsLog);
        }

        // Create SMS log
        SmsLog smsLog = SmsLog.builder()
                .student(student)
                .paymentTransaction(paymentTransaction)
                .recipientPhone(recipientPhone)
                .message(request.getMessage())
                .status(SmsLog.SmsStatus.SENT)
                .gatewayMessageId("SMS-" + UUID.randomUUID().toString().substring(0, 8))
                .sentAt(LocalDateTime.now())
                .build();

        // In a real implementation, you would call an SMS gateway here
        log.info("📲 SMS SENT: To {} - Message: {}", recipientPhone, request.getMessage());

        SmsLog savedSmsLog = smsLogRepository.save(smsLog);

        // Update payment transaction
        paymentTransaction.setSmsSent(true);
        paymentTransaction.setSmsSentAt(LocalDateTime.now());
        paymentTransaction.setSmsId(savedSmsLog.getGatewayMessageId());
        paymentTransactionRepository.save(paymentTransaction);

        log.info("✅ SMS record saved with ID: {}", savedSmsLog.getId());
        return convertToSmsLogResponse(savedSmsLog);
    }

    public List<SmsLogResponse> getSmsLogs(Long studentId, Long paymentTransactionId) {
        List<SmsLog> smsLogs;

        if (studentId != null && paymentTransactionId != null) {
            // Both filters - need custom query or filter in memory
            smsLogs = smsLogRepository.findByStudentId(studentId).stream()
                    .filter(log -> paymentTransactionId.equals(log.getPaymentTransaction().getId()))
                    .collect(Collectors.toList());
        } else if (studentId != null) {
            smsLogs = smsLogRepository.findByStudentId(studentId);
        } else if (paymentTransactionId != null) {
            smsLogs = smsLogRepository.findByPaymentTransactionId(paymentTransactionId);
        } else {
            smsLogs = smsLogRepository.findAll();
        }

        return smsLogs.stream()
                .map(this::convertToSmsLogResponse)
                .collect(Collectors.toList());
    }

    // ========== EXPORT OPERATIONS ==========

    public byte[] exportTransactionsToCsv(String type, LocalDate startDate, LocalDate endDate) {
        StringBuilder csvContent = new StringBuilder();

        if ("verified".equalsIgnoreCase(type)) {
            csvContent.append("Receipt Number,Payment Date,Amount,Student Name,Grade,Payment Method,Bank Reference,Bank Transaction ID\n");

            List<PaymentTransaction> transactions = paymentTransactionRepository
                    .findByPaymentDateBetween(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay())
                    .stream()
                    .filter(PaymentTransaction::getIsVerified)
                    .limit(100000) // Limit for performance
                    .collect(Collectors.toList());

            for (PaymentTransaction transaction : transactions) {
                csvContent.append(String.format("\"%s\",%s,%.2f,\"%s\",\"%s\",\"%s\",\"%s\",%s\n",
                        transaction.getReceiptNumber(),
                        transaction.getPaymentDate().toLocalDate(),
                        transaction.getAmount(),
                        transaction.getStudent().getFullName(),
                        transaction.getStudent().getGrade(),
                        transaction.getPaymentMethod(),
                        transaction.getBankReference() != null ? transaction.getBankReference() : "",
                        transaction.getBankTransaction() != null ? transaction.getBankTransaction().getId() : ""
                ));
            }

            log.info("📤 Exported {} verified transactions to CSV", transactions.size());

        } else if ("bank".equalsIgnoreCase(type)) {
            csvContent.append("Bank Reference,Transaction Date,Description,Amount,Status,Student Name,Bank Account,Payment Transaction ID,Receipt Number\n");

            List<BankTransaction> transactions = bankTransactionRepository
                    .findByTransactionDateBetween(startDate, endDate)
                    .stream()
                    .limit(100000) // Limit for performance
                    .collect(Collectors.toList());

            for (BankTransaction transaction : transactions) {
                csvContent.append(String.format("\"%s\",%s,\"%s\",%.2f,\"%s\",\"%s\",\"%s\",%s,\"%s\"\n",
                        transaction.getBankReference(),
                        transaction.getTransactionDate(),
                        transaction.getDescription(),
                        transaction.getAmount(),
                        transaction.getStatus(),
                        transaction.getStudent() != null ? transaction.getStudent().getFullName() : "",
                        transaction.getBankAccount() != null ? transaction.getBankAccount() : "",
                        transaction.getPaymentTransaction() != null ? transaction.getPaymentTransaction().getId() : "",
                        transaction.getPaymentTransaction() != null ? transaction.getPaymentTransaction().getReceiptNumber() : ""
                ));
            }

            log.info("📤 Exported {} bank transactions to CSV", transactions.size());
        } else {
            throw new IllegalArgumentException("Invalid export type: " + type);
        }

        return csvContent.toString().getBytes();
    }

    public byte[] generateReceiptPdf(Long transactionId) {
        log.info("📄 Looking for transaction ID: {}", transactionId);

        // First, try to find as PaymentTransaction
        Optional<PaymentTransaction> paymentTransactionOpt = paymentTransactionRepository.findById(transactionId);
        if (paymentTransactionOpt.isPresent()) {
            PaymentTransaction paymentTransaction = paymentTransactionOpt.get();
            log.info("✅ Found payment transaction: {}", paymentTransaction.getReceiptNumber());

            // Check if it's verified
            if (!paymentTransaction.getIsVerified() && paymentTransaction.getBankTransaction() == null) {
                throw new RuntimeException("Payment transaction must be verified to generate receipt");
            }

            return receiptGenerator.generateReceiptPdf(paymentTransaction);
        }

        // If not found as PaymentTransaction, try as BankTransaction
        Optional<BankTransaction> bankTransactionOpt = bankTransactionRepository.findById(transactionId);
        if (bankTransactionOpt.isPresent()) {
            BankTransaction bankTransaction = bankTransactionOpt.get();
            log.info("✅ Found bank transaction: {}", bankTransaction.getBankReference());

            // Check if it's matched or verified
            if (bankTransaction.getStatus() == TransactionStatus.MATCHED ||
                    bankTransaction.getStatus() == TransactionStatus.VERIFIED) {

                if (bankTransaction.getStudent() == null) {
                    throw new RuntimeException("Matched bank transaction must have a student assigned");
                }

                // Check if payment transaction exists
                if (bankTransaction.getPaymentTransaction() == null) {
                    log.warn("⚠️ No payment transaction found for matched bank transaction, creating one...");
                    // Create payment transaction automatically
                    PaymentTransaction paymentTransaction =
                            paymentTransactionService.createFromMatchedBankTransaction(bankTransaction);
                    log.info("📄 Created payment transaction {} for receipt generation",
                            paymentTransaction.getReceiptNumber());
                    return receiptGenerator.generateReceiptPdf(paymentTransaction);
                }

                log.info("📄 Generating receipt for auto-matched bank transaction");
                return receiptGenerator.generateReceiptPdf(bankTransaction.getPaymentTransaction());
            } else {
                throw new RuntimeException("Bank transaction must be MATCHED or VERIFIED to generate receipt");
            }
        }

        // Transaction not found
        log.error("❌ Transaction not found with ID: {}", transactionId);
        throw new RuntimeException("Transaction not found with id: " + transactionId);
    }

    // ========== IMPORT PROGRESS TRACKING ==========

    public ImportProgress getImportProgress(String importId) {
        return importProgressMap.get(importId);
    }

    public boolean cancelImport(String importId) {
        ImportProgress progress = importProgressMap.get(importId);
        if (progress != null && progress.getStatus() == ImportStatus.PROCESSING) {
            progress.setStatus(ImportStatus.CANCELLED);
            log.info("Import {} cancelled by user", importId);
            return true;
        }
        return false;
    }

    // ========== OPTIMIZATION ENDPOINTS ==========

    public StudentCacheService.CacheStats getCacheStats() {
        return StudentCacheService.CacheStats.fromService(studentCacheService);
    }

    public void refreshMatcherCache() {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Refreshing transaction matcher cache...");
                List<Student> allStudents = studentRepository.findAll();
                transactionMatcher.refreshCache(allStudents);
                log.info("✅ Transaction matcher cache refreshed with {} students", allStudents.size());
            } catch (Exception e) {
                log.error("Failed to refresh transaction matcher cache", e);
            }
        });
    }

    // ========== HELPER METHODS ==========

    private String getBestContactPhone(Student student) {
        if (student == null) return null;

        // Try student's own phone first
        if (student.getPhone() != null && !student.getPhone().trim().isEmpty()) {
            String phone = student.getPhone().trim();
            if (isValidIndianPhoneNumber(phone)) {
                return phone;
            }
        }

        // Try emergency contact phone
        if (student.getEmergencyContactPhone() != null &&
                !student.getEmergencyContactPhone().trim().isEmpty()) {
            String phone = student.getEmergencyContactPhone().trim();
            if (isValidIndianPhoneNumber(phone)) {
                return phone;
            }
        }

        return null;
    }

    private String cleanPhoneNumber(String phone) {
        if (phone == null) return null;

        // Remove all non-digit characters except leading +
        String cleaned = phone.replaceAll("[^\\d+]", "");

        // If it starts with +91, keep it, otherwise ensure it's 10 digits
        if (cleaned.startsWith("+91") && cleaned.length() == 13) {
            return cleaned;
        } else if (cleaned.startsWith("91") && cleaned.length() == 12) {
            return "+" + cleaned;
        } else if (cleaned.length() == 10) {
            return "+91" + cleaned;
        } else if (cleaned.length() > 10) {
            // Take last 10 digits
            return "+91" + cleaned.substring(cleaned.length() - 10);
        }

        return cleaned;
    }

    private boolean isValidIndianPhoneNumber(String phone) {
        if (phone == null) return false;

        String cleaned = phone.replaceAll("[^\\d]", "");

        // Check if it's a valid Indian mobile number (10 digits starting with 6-9)
        if (cleaned.matches("^[6-9]\\d{9}$")) {
            return true;
        }

        // Check if it's a valid Indian number with country code
        if (cleaned.matches("^91[6-9]\\d{9}$")) {
            return true;
        }

        return false;
    }

    // ========== CONVERSION METHODS ==========

    private BankTransactionResponse convertToBankTransactionResponse(BankTransaction transaction) {
        BankTransactionResponse response = new BankTransactionResponse();
        response.setId(transaction.getId());
        response.setBankReference(transaction.getBankReference());
        response.setTransactionDate(transaction.getTransactionDate());
        response.setDescription(transaction.getDescription());
        response.setAmount(transaction.getAmount());
        response.setBankAccount(transaction.getBankAccount());
        response.setStatus(transaction.getStatus());
        response.setPaymentMethod(transaction.getPaymentMethod());
        response.setImportedAt(transaction.getImportedAt());
        response.setMatchedAt(transaction.getMatchedAt());
        response.setFileName(transaction.getFileName());
        response.setImportBatchId(transaction.getImportBatchId());

        // SMS fields
        response.setSmsSent(transaction.getSmsSent());
        response.setSmsSentAt(transaction.getSmsSentAt());
        response.setSmsId(transaction.getSmsId());

        // ========== NEW: ADD PAYMENT TRANSACTION INFO ==========
        if (transaction.getPaymentTransaction() != null) {
            PaymentTransaction pt = transaction.getPaymentTransaction();
            response.setPaymentTransactionId(pt.getId());
            response.setReceiptNumber(pt.getReceiptNumber());
            response.setPaymentVerified(pt.getIsVerified());
            response.setPaymentVerifiedAt(pt.getVerifiedAt());
        }

        if (transaction.getStudent() != null) {
            Student student = transaction.getStudent();
            response.setStudentId(student.getId());
            response.setStudentName(student.getFullName());
            response.setStudentGrade(student.getGrade());

            // Set fee information
            response.setStudentPendingAmount(student.getPendingAmount());
            response.setStudentFeeStatus(student.getFeeStatus());
            response.setStudentTotalFee(student.getTotalFee());
            response.setStudentPaidAmount(student.getPaidAmount());

            if (student.getPendingAmount() != null && student.getTotalFee() != null &&
                    student.getTotalFee() > 0) {
                double percentage = (student.getPaidAmount() != null ? student.getPaidAmount() : 0.0) /
                        student.getTotalFee() * 100;
                response.setStudentPaymentPercentage(percentage);
            }
        }

        return response;
    }

    private PaymentTransactionResponse convertToPaymentTransactionResponse(PaymentTransaction transaction) {
        PaymentTransactionResponse response = new PaymentTransactionResponse();
        response.setId(transaction.getId());
        response.setReceiptNumber(transaction.getReceiptNumber());
        response.setAmount(transaction.getAmount());
        response.setPaymentMethod(transaction.getPaymentMethod());
        response.setPaymentDate(transaction.getPaymentDate());
        response.setIsVerified(transaction.getIsVerified());
        response.setVerifiedAt(transaction.getVerifiedAt());
        response.setSmsSent(transaction.getSmsSent());
        response.setSmsSentAt(transaction.getSmsSentAt());
        response.setNotes(transaction.getNotes());
        response.setPaymentFor(transaction.getPaymentFor());
        response.setDiscountApplied(transaction.getDiscountApplied());
        response.setLateFeePaid(transaction.getLateFeePaid());
        response.setConvenienceFee(transaction.getConvenienceFee());
        response.setTotalPaid(transaction.getTotalPaid());
        response.setCreatedAt(transaction.getCreatedAt());

        if (transaction.getStudent() != null) {
            Student student = transaction.getStudent();
            response.setStudentId(student.getId());
            response.setStudentName(student.getFullName());
            response.setStudentGrade(student.getGrade());

            // Set fee information
            response.setStudentPendingAmount(student.getPendingAmount());
            response.setStudentFeeStatus(student.getFeeStatus());
            response.setStudentTotalFee(student.getTotalFee());
            response.setStudentPaidAmount(student.getPaidAmount());

            if (student.getPendingAmount() != null && student.getTotalFee() != null &&
                    student.getTotalFee() > 0) {
                double percentage = (student.getPaidAmount() != null ? student.getPaidAmount() : 0.0) /
                        student.getTotalFee() * 100;
                response.setStudentPaymentPercentage(percentage);
            }
        }

        if (transaction.getBankTransaction() != null) {
            response.setBankTransactionId(transaction.getBankTransaction().getId());
            response.setBankReference(transaction.getBankTransaction().getBankReference());
        }

        return response;
    }

    private SmsLogResponse convertToSmsLogResponse(SmsLog smsLog) {
        SmsLogResponse response = new SmsLogResponse();
        response.setId(smsLog.getId());
        response.setRecipientPhone(smsLog.getRecipientPhone());
        response.setMessage(smsLog.getMessage());
        response.setStatus(smsLog.getStatus().toString());
        response.setGatewayMessageId(smsLog.getGatewayMessageId());
        response.setDeliveryStatus(smsLog.getDeliveryStatus());
        response.setSentAt(smsLog.getSentAt());
        response.setDeliveredAt(smsLog.getDeliveredAt());

        if (smsLog.getStudent() != null) {
            response.setStudentId(smsLog.getStudent().getId());
            response.setStudentName(smsLog.getStudent().getFullName());
        }

        if (smsLog.getPaymentTransaction() != null) {
            response.setPaymentTransactionId(smsLog.getPaymentTransaction().getId());
            response.setReceiptNumber(smsLog.getPaymentTransaction().getReceiptNumber());
        }

        return response;
    }

    // ========== INNER CLASSES ==========

    public static class ImportProgress {
        // Getters and setters
        @Getter
        private final String importId;
        @Getter
        private final String fileName;
        @Getter
        private final long fileSize;
        @Getter
        private ImportStatus status;
        @Setter
        @Getter
        private int totalLines;
        @Setter
        @Getter
        private int processedLines;
        @Getter
        @Setter
        private int savedLines;
        @Getter
        private int failedLines;
        @Getter
        private int matchedCount;
        @Getter
        private String currentStatus;
        @Getter
        @Setter
        private String error;
        private long startTime;
        private long endTime;
        @Getter
        @Setter
        private int currentBatch;
        @Getter
        @Setter
        private int totalBatches;

        public ImportProgress(String importId, String fileName, long fileSize) {
            this.importId = importId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.startTime = System.currentTimeMillis();
            this.status = ImportStatus.QUEUED;
            this.currentStatus = "Queued for processing";
        }

        public double getProgressPercentage() {
            if (totalLines == 0) return 0;
            return (processedLines * 100.0) / totalLines;
        }

        public long getElapsedTime() {
            if (endTime > 0) {
                return endTime - startTime;
            }
            return System.currentTimeMillis() - startTime;
        }

        public void incrementFailedLines(int count) {
            this.failedLines += count;
        }

        public void updateStatus(String status) {
            this.currentStatus = status;
        }

        public void setStatus(ImportStatus status) {
            this.status = status;
            if (status == ImportStatus.COMPLETED || status == ImportStatus.FAILED) {
                this.endTime = System.currentTimeMillis();
            }
        }

        public void setMatchedCount(int matchedCount) { this.matchedCount = matchedCount; }
    }

    public enum ImportStatus {
        QUEUED, PROCESSING, COMPLETED, FAILED, CANCELLED
    }
    // ========== NEW METHODS FOR CONTROLLER ==========

    public List<BankTransactionResponse> getAllBankTransactions(
            TransactionStatus status, String search, LocalDate fromDate, LocalDate toDate) {

        List<BankTransaction> transactions;

        if (status != null && search != null && !search.trim().isEmpty()) {
            // Combined filter: status + search
            Page<BankTransaction> searchResult = bankTransactionRepository.searchTransactions(
                    search, Pageable.unpaged());
            transactions = searchResult.getContent().stream()
                    .filter(t -> t.getStatus() == status)
                    .collect(Collectors.toList());

        } else if (status != null) {
            // Filter by status only
            transactions = bankTransactionRepository.findByStatus(status);

        } else if (search != null && !search.trim().isEmpty()) {
            // Search only
            Page<BankTransaction> searchResult = bankTransactionRepository.searchTransactions(
                    search, Pageable.unpaged());
            transactions = searchResult.getContent();

        } else {
            // Get all transactions
            transactions = bankTransactionRepository.findAll();
        }

        // Apply date filters if provided
        if (fromDate != null) {
            transactions = transactions.stream()
                    .filter(t -> !t.getTransactionDate().isBefore(fromDate))
                    .collect(Collectors.toList());
        }

        if (toDate != null) {
            transactions = transactions.stream()
                    .filter(t -> !t.getTransactionDate().isAfter(toDate))
                    .collect(Collectors.toList());
        }

        // Sort by date, most recent first
        transactions.sort((a, b) -> b.getTransactionDate().compareTo(a.getTransactionDate()));

        return transactions.stream()
                .map(this::convertToBankTransactionResponse)
                .collect(Collectors.toList());
    }

    public Page<BankTransactionResponse> getBankTransactions(
            TransactionStatus status, String search, LocalDate fromDate, LocalDate toDate, Pageable pageable) {

        Page<BankTransaction> transactions;

        if (status != null && search != null && !search.trim().isEmpty()) {
            // Combined filter with pagination
            Page<BankTransaction> searchResult = bankTransactionRepository.searchTransactions(search, pageable);
            List<BankTransaction> filtered = searchResult.getContent().stream()
                    .filter(t -> t.getStatus() == status)
                    .collect(Collectors.toList());

            transactions = new PageImpl<>(filtered, pageable, filtered.size());

        } else if (status != null) {
            // Filter by status with pagination
            transactions = bankTransactionRepository.findByStatus(status, pageable);

        } else if (search != null && !search.trim().isEmpty()) {
            // Search with pagination
            transactions = bankTransactionRepository.searchTransactions(search, pageable);

        } else {
            // Get all with pagination
            transactions = bankTransactionRepository.findAll(pageable);
        }

        // Apply date filters if provided
        if (fromDate != null || toDate != null) {
            List<BankTransaction> filtered = transactions.getContent().stream()
                    .filter(t -> {
                        if (fromDate != null && t.getTransactionDate().isBefore(fromDate)) {
                            return false;
                        }
                        if (toDate != null && t.getTransactionDate().isAfter(toDate)) {
                            return false;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            transactions = new PageImpl<>(filtered, pageable, filtered.size());
        }

        return transactions.map(this::convertToBankTransactionResponse);
    }

    public List<PaymentTransactionResponse> getAllVerifiedTransactions(String search) {
        List<PaymentTransaction> transactions;

        if (search != null && !search.trim().isEmpty()) {
            Page<PaymentTransaction> searchResult = paymentTransactionRepository.searchTransactions(
                    search, Pageable.unpaged());
            transactions = searchResult.getContent().stream()
                    .filter(PaymentTransaction::getIsVerified)
                    .collect(Collectors.toList());
        } else {
            // Get all verified transactions
            transactions = paymentTransactionRepository.findByIsVerified(true);
        }

        // Sort by payment date, most recent first
        transactions.sort((a, b) -> b.getPaymentDate().compareTo(a.getPaymentDate()));

        return transactions.stream()
                .map(this::convertToPaymentTransactionResponse)
                .collect(Collectors.toList());
    }

    // Statistics helper methods
    public long getTotalBankTransactionCount() {
        return bankTransactionRepository.count();
    }

    public long getBankTransactionCountByStatus(TransactionStatus status) {
        Long count = bankTransactionRepository.countByStatus(status);
        return count != null ? count : 0L;
    }

    public long getPaymentTransactionCountVerified() {
        Long count = paymentTransactionRepository.countByIsVerified(true);
        return count != null ? count : 0L;
    }

    public long getBankTransactionCountSince(LocalDate sinceDate) {
        List<BankTransaction> recent = bankTransactionRepository
                .findByTransactionDateBetween(sinceDate, LocalDate.now());
        return recent.size();
    }

    public Map<String, Double> getRecentAmountsByStatus(LocalDate startDate, LocalDate endDate) {
        List<BankTransaction> recentTransactions = bankTransactionRepository
                .findByTransactionDateBetween(startDate, endDate);

        return recentTransactions.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getStatus().name(),
                        Collectors.summingDouble(BankTransaction::getAmount)
                ));
    }

    public double getTotalPendingFees() {
        return studentRepository.findAll().stream()
                .mapToDouble(s -> s.getPendingAmount() != null ? s.getPendingAmount() : 0.0)
                .sum();
    }

    /**
     * Get total amount of all verified payments
     */
    public Double getTotalVerifiedAmount() {
        try {
            Double amount = paymentTransactionRepository.getTotalVerifiedAmount();
            return amount != null ? amount : 0.0;
        } catch (Exception e) {
            log.error("Error getting total verified amount", e);
            return 0.0;
        }
    }

    /**
     * Get total amount of verified payments made today
     */
    public Double getTotalVerifiedAmountToday() {
        try {
            Double amount = paymentTransactionRepository.getTotalVerifiedAmountToday();
            return amount != null ? amount : 0.0;
        } catch (Exception e) {
            log.error("Error getting today's verified amount", e);
            return 0.0;
        }
    }
}