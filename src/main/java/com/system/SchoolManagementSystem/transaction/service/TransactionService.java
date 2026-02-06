package com.system.SchoolManagementSystem.transaction.service;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.termmanagement.dto.request.PaymentApplicationRequest;
import com.system.SchoolManagementSystem.termmanagement.dto.response.PaymentApplicationResponse;
import com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment;
import com.system.SchoolManagementSystem.termmanagement.entity.TermFeeItem;
import com.system.SchoolManagementSystem.termmanagement.repository.StudentTermAssignmentRepository;
import com.system.SchoolManagementSystem.termmanagement.repository.TermFeeItemRepository;
import com.system.SchoolManagementSystem.termmanagement.service.TermFeeService;
import com.system.SchoolManagementSystem.transaction.validation.TransactionValidationService;
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
    private final StudentTermAssignmentRepository studentTermAssignmentRepository; // ADDED

    // ========== NEW TERM MANAGEMENT INTEGRATION ==========
    private final TermFeeService termFeeService;
    private final TermFeeItemRepository termFeeItemRepository;

    // ========== UTILITIES ==========
    private final ReceiptGenerator receiptGenerator;
    private final BankStatementParser bankStatementParser;

    // ========== NEW OPTIMIZATION COMPONENTS ==========
    private final TransactionMatcher transactionMatcher;
    private final StudentCacheService studentCacheService;
    private final StudentFeeUpdateService studentFeeUpdateService;
    private final PaymentTransactionService paymentTransactionService;

    // ========== PERFORMANCE MONITORING ==========
    private final Map<String, ImportProgress> importProgressMap = new ConcurrentHashMap<>();

    // ========== VALIDATION SERVICE ==========
    private final TransactionValidationService transactionValidationService; // ADD THIS

    // ========== INITIALIZATION ==========

    @PostConstruct
    public void initialize() {
        log.info("Initializing TransactionService with single cache system...");

        // Log cache status after a short delay
        CompletableFuture.runAsync(() -> {
            try {
                // Wait a bit for cache to load
                Thread.sleep(3000);

                log.info("📊 Cache Status:");
                log.info("  StudentCacheService loaded: {}", studentCacheService.isCacheLoaded());

                if (studentCacheService.isCacheLoaded()) {
                    Set<String> names = studentCacheService.getAllNames();
                    log.info("  StudentCacheService names: {}", names.size());

                    if (!names.isEmpty()) {
                        log.info("  Sample cached names:");
                        names.stream().limit(5).forEach(name -> log.info("    - {}", name));
                    }
                }
            } catch (Exception e) {
                log.error("Error checking cache status", e);
            }
        });
    }

    // ========== CACHE MANAGEMENT METHODS ==========

    /**
     * Get cache statistics for optimization
     */
    public StudentCacheService.CacheStats getCacheStats() {
        return StudentCacheService.CacheStats.fromService(studentCacheService);
    }

    /**
     * Refresh cache
     */
    public void refreshMatcherCache() {
        log.info("🔄 Refreshing student cache...");
        studentCacheService.refreshCache();
        log.info("✅ Student cache refresh initiated");
    }

    /**
     * Get bank transaction count by status
     */
    public long getBankTransactionCountByStatus(TransactionStatus status) {
        if (status == null) return 0;
        Long count = bankTransactionRepository.countByStatus(status);
        return count != null ? count : 0L;
    }

    public Map<String, Object> verifyCacheStatus() {
        Map<String, Object> status = new HashMap<>();

        // Check StudentCacheService
        boolean isCacheLoaded = studentCacheService.isCacheLoaded();
        status.put("studentCacheLoaded", isCacheLoaded);

        if (isCacheLoaded) {
            Set<String> names = studentCacheService.getAllNames();
            status.put("studentCacheSize", names.size());

            // Get sample names
            List<String> sampleNames = names.stream()
                    .limit(5)
                    .collect(Collectors.toList());
            status.put("sampleNames", sampleNames);
        }

        // Check database
        long studentCount = studentRepository.count();
        status.put("databaseStudentCount", studentCount);

        // Check if students have data
        List<Student> sampleStudents = studentRepository.findAll()
                .stream()
                .limit(3)
                .collect(Collectors.toList());

        status.put("sampleStudents", sampleStudents.stream()
                .map(s -> Map.of(
                        "id", s.getId(),
                        "studentId", s.getStudentId(),
                        "fullName", s.getFullName(),
                        "grade", s.getGrade(),
                        "hasName", s.getFullName() != null && !s.getFullName().isEmpty()
                ))
                .collect(Collectors.toList()));

        // Check if any bank transactions exist
        long bankTxCount = bankTransactionRepository.count();
        status.put("bankTransactionCount", bankTxCount);

        log.info("🔧 Cache verification: loaded={}, students={}, bankTx={}",
                isCacheLoaded, studentCount, bankTxCount);

        return status;
    }

    // ========== BANK TRANSACTION OPERATIONS ==========

    public List<BankTransactionResponse> importBankTransactions(BankTransactionImportRequest request) {
        log.info("📥 Importing bank transactions synchronously: {}",
                request.getFile().getOriginalFilename());

        // ========== DEBUG: Check cache before import ==========
        log.info("🔧 Checking cache status before import...");
        Map<String, Object> cacheStatus = verifyCacheStatus();
        log.info("Cache status: {}", cacheStatus);

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

            log.info("✅ Parser returned {} transactions", transactions.size());

            // Use optimized auto-matching with single cache
            List<BankTransaction> matchedTransactions = autoMatchTransactionsOptimized(transactions);

            // Save transactions in batches for performance
            List<BankTransaction> savedTransactions = saveTransactionsInBatches(matchedTransactions);

            long autoMatchedCount = savedTransactions.stream()
                    .filter(bt -> bt.getStudent() != null)
                    .count();

            log.info("✅ Synchronous import completed: {} transactions, {} auto-matched",
                    savedTransactions.size(), autoMatchedCount);

            // ========== BATCH QUERY TERM ASSIGNMENTS ==========
            Map<Long, Boolean> hasAssignmentsMap = new HashMap<>();
            Map<Long, Integer> assignmentCountMap = new HashMap<>();

            // Collect unique student IDs
            Set<Long> studentIds = savedTransactions.stream()
                    .filter(bt -> bt.getStudent() != null)
                    .map(bt -> bt.getStudent().getId())
                    .collect(Collectors.toSet());

            if (!studentIds.isEmpty()) {
                log.info("🔍 Batch querying term assignments for {} students", studentIds.size());

                try {
                    // Use batch query - single database call
                    List<Object[]> batchResults = studentTermAssignmentRepository
                            .batchGetTermAssignmentInfo(studentIds);

                    // Process results
                    for (Object[] result : batchResults) {
                        Long studentId = ((Number) result[0]).longValue();
                        Boolean hasAssignments = (Boolean) result[1];
                        Integer assignmentCount = ((Number) result[2]).intValue();

                        hasAssignmentsMap.put(studentId, hasAssignments != null ? hasAssignments : false);
                        assignmentCountMap.put(studentId, assignmentCount != null ? assignmentCount : 0);
                    }

                    // For students not in the results (no term assignments)
                    for (Long studentId : studentIds) {
                        if (!hasAssignmentsMap.containsKey(studentId)) {
                            hasAssignmentsMap.put(studentId, false);
                            assignmentCountMap.put(studentId, 0);
                        }
                    }

                    log.info("✅ Retrieved term assignment info for {} students", hasAssignmentsMap.size());

                } catch (Exception e) {
                    log.error("❌ Error batch querying term assignments: {}", e.getMessage());
                    // Set defaults for all students
                    for (Long studentId : studentIds) {
                        hasAssignmentsMap.put(studentId, false);
                        assignmentCountMap.put(studentId, 0);
                    }
                }
            }

            // Convert to response DTOs with pre-fetched data
            return savedTransactions.stream()
                    .map(transaction -> convertToBankTransactionResponseWithTermData(
                            transaction, hasAssignmentsMap, assignmentCountMap))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Failed to import bank transactions", e);
            throw new RuntimeException("Failed to import transactions: " + e.getMessage());
        }
    }

    private List<BankTransaction> autoMatchTransactionsOptimized(List<BankTransaction> transactions) {
        log.info("🚀 Starting auto-matching for {} transactions", transactions.size());

        // Collect student IDs from matched transactions
        Set<Long> matchedStudentIds = new HashSet<>();
        Map<Long, List<BankTransaction>> transactionsByStudent = new HashMap<>();

        // First pass: Match students (existing logic)
        for (BankTransaction transaction : transactions) {
            Optional<Student> matchedStudent = transactionMatcher.findMatchingStudent(transaction);

            if (matchedStudent.isPresent()) {
                Student student = matchedStudent.get();
                transaction.setStudent(student);
                transaction.setStatus(TransactionStatus.MATCHED);

                matchedStudentIds.add(student.getId());
                transactionsByStudent.computeIfAbsent(student.getId(), k -> new ArrayList<>())
                        .add(transaction);
            }
        }

        // ========== CRITICAL: VALIDATE BEFORE PROCESSING ==========
        if (!matchedStudentIds.isEmpty()) {
            log.info("🔍 Validating {} matched students before processing", matchedStudentIds.size());

            Map<Long, TransactionValidationService.ValidationResult> validationResults =
                    transactionValidationService.batchValidateStudents(matchedStudentIds);

            int invalidCount = 0;
            List<Long> invalidStudentIds = new ArrayList<>();

            for (Map.Entry<Long, TransactionValidationService.ValidationResult> entry :
                    validationResults.entrySet()) {

                Long studentId = entry.getKey();
                TransactionValidationService.ValidationResult result = entry.getValue();

                if (!result.isValid()) {
                    invalidCount++;
                    invalidStudentIds.add(studentId);

                    // Mark all transactions for this student as invalid
                    List<BankTransaction> studentTransactions = transactionsByStudent.get(studentId);
                    if (studentTransactions != null) {
                        for (BankTransaction txn : studentTransactions) {
                            txn.setStatus(TransactionStatus.UNVERIFIED);
                            txn.setStudent(null); // Unlink student
                            txn.setNotes("VALIDATION FAILED: " + result.getMessage());

                            log.error("❌ Transaction {} rejected for student {}: {}",
                                    txn.getBankReference(),
                                    txn.getDescription(),
                                    result.getMessage());
                        }
                    }
                }
            }

            if (invalidCount > 0) {
                log.error("🚫 Rejected transactions for {} students: {}",
                        invalidCount, invalidStudentIds);

                // Remove invalid students from processing
                invalidStudentIds.forEach(matchedStudentIds::remove);
            }
        }
        // ========================================================

        // Process only validated transactions
        List<BankTransaction> validTransactions = new ArrayList<>();

        for (BankTransaction transaction : transactions) {
            if (transaction.getStudent() != null &&
                    transaction.getStatus() == TransactionStatus.MATCHED &&
                    matchedStudentIds.contains(transaction.getStudent().getId())) {

                validTransactions.add(transaction);
            }
        }

        log.info("✅ After validation: {} valid transactions of {}",
                validTransactions.size(), transactions.size());

        return validTransactions;
    }

    private List<BankTransaction> saveTransactionsInBatches(List<BankTransaction> transactions) {
        if (transactions.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("💾 Starting batch save for {} transactions...", transactions.size());

        List<BankTransaction> savedTransactions = new ArrayList<>();
        int batchSize = 1000;
        int totalBatches = (transactions.size() + batchSize - 1) / batchSize;

        for (int i = 0; i < transactions.size(); i += batchSize) {
            int end = Math.min(i + batchSize, transactions.size());
            List<BankTransaction> batch = transactions.subList(i, end);

            try {
                log.info("  Saving batch {}/{} ({} transactions)",
                        (i/batchSize) + 1, totalBatches, batch.size());

                // ========== PRE-VALIDATION CHECK ==========
                List<BankTransaction> validBatch = new ArrayList<>();
                List<BankTransaction> invalidBatch = new ArrayList<>();

                for (BankTransaction transaction : batch) {
                    if (transaction.getStudent() == null) {
                        // No student matched - keep as UNVERIFIED
                        invalidBatch.add(transaction);
                        continue;
                    }

                    // Validate student has term assignments and fees
                    TransactionValidationService.ValidationResult validation =
                            transactionValidationService.validateStudentForPayment(
                                    transaction.getStudent().getId(),
                                    transaction.getStudent().getFullName()
                            );

                    if (!validation.isValid()) {
                        log.error("❌ Transaction {} rejected: {}",
                                transaction.getBankReference(), validation.getMessage());

                        transaction.setStatus(TransactionStatus.UNVERIFIED);
                        transaction.setStudent(null);
                        transaction.setNotes("REJECTED: " + validation.getMessage());
                        invalidBatch.add(transaction);
                    } else {
                        validBatch.add(transaction);
                    }
                }

                log.info("    Batch {}: {} valid, {} invalid",
                        (i/batchSize) + 1, validBatch.size(), invalidBatch.size());

                // Save invalid transactions (marked as UNVERIFIED)
                if (!invalidBatch.isEmpty()) {
                    bankTransactionRepository.saveAll(invalidBatch);
                    savedTransactions.addAll(invalidBatch);
                }

                // Process valid transactions
                if (!validBatch.isEmpty()) {
                    List<BankTransaction> savedValidBatch = bankTransactionRepository.saveAll(validBatch);
                    savedTransactions.addAll(savedValidBatch);

                    // Now process payments for valid transactions
                    processPaymentsForValidTransactions(savedValidBatch);
                }
                // ==========================================

            } catch (Exception e) {
                log.error("❌ Failed to save batch {}-{}: {}", i, end, e.getMessage(), e);
            }
        }

        return savedTransactions;
    }

    private void processPaymentsForValidTransactions(List<BankTransaction> validTransactions) {
        int paymentCreatedCount = 0;
        int feeAppliedCount = 0;

        for (BankTransaction savedTransaction : validTransactions) {
            if (savedTransaction.getStudent() != null &&
                    savedTransaction.getStatus() == TransactionStatus.MATCHED &&
                    savedTransaction.getPaymentTransaction() == null) {

                try {
                    PaymentTransaction paymentTransaction =
                            paymentTransactionService.createFromMatchedBankTransaction(savedTransaction);

                    // Apply payment to term fees
                    try {
                        PaymentApplicationRequest feeRequest = new PaymentApplicationRequest();
                        feeRequest.setStudentId(savedTransaction.getStudent().getId());
                        feeRequest.setAmount(savedTransaction.getAmount());
                        feeRequest.setReference(savedTransaction.getBankReference());
                        feeRequest.setNotes("Auto-matched from bank import");

                        PaymentApplicationResponse feeResponse = termFeeService.applyPaymentToStudent(feeRequest);
                        feeAppliedCount++;

                        log.info("✅ Payment applied: {} +₹{} (Receipt: {}), Pending: ₹{}",
                                savedTransaction.getStudent().getFullName(),
                                savedTransaction.getAmount(),
                                paymentTransaction.getReceiptNumber(),
                                feeResponse.getRemainingPayment());

                    } catch (Exception feeError) {
                        log.warn("⚠️ Failed to apply payment to term fees: {}", feeError.getMessage());
                    }

                    sendAutoMatchSms(
                            savedTransaction.getStudent(),
                            savedTransaction,
                            paymentTransaction
                    );

                    paymentCreatedCount++;

                } catch (Exception e) {
                    log.warn("Failed to create payment for transaction {}: {}",
                            savedTransaction.getBankReference(), e.getMessage());
                }
            }
        }

        log.info("💰 Processed {} payments, applied {} fee payments",
                paymentCreatedCount, feeAppliedCount);
    }

    @Async
    private void sendAutoMatchSms(Student student, BankTransaction transaction, PaymentTransaction paymentTransaction) {
        try {
            log.info("📱 Attempting to send auto-match SMS for payment: {}",
                    paymentTransaction.getReceiptNumber());

            String recipientPhone = getBestContactPhone(student);
            if (recipientPhone == null || recipientPhone.trim().isEmpty()) {
                log.warn("📵 No valid phone number available for student {} to send auto-match SMS",
                        student.getFullName());
                return;
            }

            recipientPhone = cleanPhoneNumber(recipientPhone);
            if (!isValidIndianPhoneNumber(recipientPhone)) {
                log.warn("📵 Invalid phone number format for student {}: {}", student.getFullName(), recipientPhone);
                return;
            }

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

            SmsLog smsLog = SmsLog.builder()
                    .student(student)
                    .paymentTransaction(paymentTransaction)
                    .recipientPhone(recipientPhone)
                    .message(message)
                    .status(SmsLog.SmsStatus.SENT)
                    .gatewayMessageId("AUTO-" + UUID.randomUUID().toString().substring(0, 8))
                    .gatewayResponse("Auto-match SMS sent successfully")
                    .sentAt(LocalDateTime.now())
                    .build();

            SmsLog savedSmsLog = smsLogRepository.save(smsLog);

            transaction.setSmsSent(true);
            transaction.setSmsSentAt(LocalDateTime.now());
            transaction.setSmsId(savedSmsLog.getGatewayMessageId());
            bankTransactionRepository.save(transaction);

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

    public Page<BankTransactionResponse> getBankTransactions(TransactionStatus status, String search, Pageable pageable) {
        try {
            Page<BankTransaction> transactions;

            if (status != null && search != null && !search.trim().isEmpty()) {
                transactions = bankTransactionRepository.searchTransactions(search, pageable);
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

        transaction.setStudent(student);
        transaction.setStatus(TransactionStatus.MATCHED);
        transaction.setMatchedAt(LocalDateTime.now());

        try {
            PaymentTransaction paymentTransaction =
                    paymentTransactionService.createFromMatchedBankTransaction(transaction);

            try {
                PaymentApplicationRequest feeRequest = new PaymentApplicationRequest();
                feeRequest.setStudentId(student.getId());
                feeRequest.setAmount(transaction.getAmount());
                feeRequest.setReference(transaction.getBankReference());
                feeRequest.setNotes("Manual match from bank transaction");

                PaymentApplicationResponse feeResponse = termFeeService.applyPaymentToStudent(feeRequest);

                log.info("💰 Term fees updated via manual match: {} +₹{} (Receipt: {}), All paid: {}",
                        student.getFullName(), transaction.getAmount(),
                        paymentTransaction.getReceiptNumber(), feeResponse.getAllPaid());

            } catch (Exception feeError) {
                log.error("⚠️ Failed to apply payment to term fees: {}", feeError.getMessage());
                transaction.setStudent(null);
                transaction.setStatus(TransactionStatus.UNVERIFIED);
                transaction.setPaymentTransaction(null);
                throw new RuntimeException("Failed to apply payment to term fees: " + feeError.getMessage());
            }

        } catch (Exception feeError) {
            log.error("⚠️ Failed to create payment: {}", feeError.getMessage());
            transaction.setStudent(null);
            transaction.setStatus(TransactionStatus.UNVERIFIED);
            transaction.setPaymentTransaction(null);
            throw new RuntimeException("Failed to create payment transaction: " + feeError.getMessage());
        }

        BankTransaction savedTransaction = bankTransactionRepository.save(transaction);

        if (savedTransaction.getPaymentTransaction() != null) {
            sendAutoMatchSms(student, savedTransaction, savedTransaction.getPaymentTransaction());
        }

        log.info("✅ Manually matched transaction {} to student {}", transactionId, student.getFullName());
        return convertToBankTransactionResponse(savedTransaction);
    }

    public void deleteBankTransaction(Long id) {
        BankTransaction transaction = bankTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        PaymentTransaction paymentTransaction = transaction.getPaymentTransaction();

        if (transaction.getStudent() != null && transaction.getAmount() != null) {
            log.warn("⚠️ Need to implement term fee reversal for deleted transaction: {}",
                    transaction.getBankReference());
        }

        if (paymentTransaction != null) {
            paymentTransactionRepository.delete(paymentTransaction);
            log.info("🗑️ Deleted linked payment transaction: {}", paymentTransaction.getReceiptNumber());
        }

        bankTransactionRepository.delete(transaction);
        log.info("🗑️ Deleted bank transaction with id: {}", id);
    }

    // ========== PAYMENT TRANSACTION OPERATIONS ==========

    public PaymentTransactionResponse verifyPayment(PaymentVerificationRequest request) {
        log.info("🔐 Verifying payment for bank transaction: {}", request.getBankTransactionId());

        BankTransaction bankTransaction = bankTransactionRepository.findById(request.getBankTransactionId())
                .orElseThrow(() -> new RuntimeException("Bank transaction not found"));

        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new RuntimeException("Student not found"));

        PaymentTransaction paymentTransaction =
                paymentTransactionService.getOrCreateForBankTransactionId(request.getBankTransactionId());

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

            bankTransaction.setStudent(student);
            bankTransaction.setStatus(TransactionStatus.VERIFIED);
            bankTransactionRepository.save(bankTransaction);

            paymentTransactionRepository.save(paymentTransaction);
        }

        PaymentTransaction verifiedTransaction =
                paymentTransactionService.verifyPaymentTransaction(paymentTransaction.getId());

        try {
            PaymentApplicationRequest feeRequest = new PaymentApplicationRequest();
            feeRequest.setStudentId(student.getId());
            feeRequest.setAmount(request.getAmount());
            feeRequest.setReference(bankTransaction.getBankReference());
            feeRequest.setNotes("Payment verification: " + request.getNotes());

            PaymentApplicationResponse feeResponse = termFeeService.applyPaymentToStudent(feeRequest);

            log.info("💰 Term fees updated via payment verification: {} +₹{} (Receipt: {}), All paid: {}",
                    student.getFullName(), request.getAmount(),
                    verifiedTransaction.getReceiptNumber(), feeResponse.getAllPaid());

        } catch (Exception feeError) {
            log.error("⚠️ Failed to update term fees during verification: {}", feeError.getMessage());
        }

        log.info("✅ Payment verified: Receipt {} for student {} - ₹{}",
                verifiedTransaction.getReceiptNumber(),
                student.getFullName(),
                verifiedTransaction.getAmount());

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

                if (bankTransaction.getStudent() == null) {
                    log.warn("⚠️ Skipping transaction {} - not matched to any student", bankTransactionId);
                    continue;
                }

                PaymentTransaction paymentTransaction =
                        paymentTransactionService.getOrCreateForBankTransactionId(bankTransactionId);

                PaymentVerificationRequest singleRequest = new PaymentVerificationRequest();
                singleRequest.setBankTransactionId(bankTransactionId);
                singleRequest.setStudentId(bankTransaction.getStudent().getId());
                singleRequest.setAmount(bankTransaction.getAmount());
                singleRequest.setPaymentMethod(bankTransaction.getPaymentMethod());
                singleRequest.setSendSms(request.getSendSms());
                singleRequest.setNotes(request.getNotes());
                singleRequest.setPaymentFor("SCHOOL_FEE");

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

                List<PaymentTransaction> verifiedList = transactions.getContent().stream()
                        .filter(PaymentTransaction::getIsVerified)
                        .collect(Collectors.toList());

                Page<PaymentTransaction> verifiedPage = new PageImpl<>(
                        verifiedList,
                        pageable,
                        verifiedList.size()
                );

                return verifiedPage.map(this::convertToPaymentTransactionResponse);

            } else {
                transactions = paymentTransactionRepository.findByIsVerified(true, pageable);
                return transactions.map(this::convertToPaymentTransactionResponse);
            }

        } catch (Exception e) {
            log.error("❌ Error getting verified transactions", e);
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

    // ========== NEW METHODS FOR TERM FEE INTEGRATION ==========

    public Map<String, Object> getStudentFeeBreakdown(Long studentId) {
        Map<String, Object> breakdown = new HashMap<>();

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + studentId));

        List<StudentTermAssignment> termAssignments = studentTermAssignmentRepository
                .findByStudentId(studentId);

        List<StudentFeeAssignment> feeAssignments = feeAssignmentRepository
                .findByStudentId(studentId);

        double totalTermFee = termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getTotalTermFee)
                .sum();

        double totalPaid = termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getPaidAmount)
                .sum();

        double totalPending = termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getPendingAmount)
                .sum();

        Optional<StudentTermAssignment> currentAssignment = termAssignments.stream()
                .filter(ta -> ta.getAcademicTerm() != null && ta.getAcademicTerm().getIsCurrent())
                .findFirst();

        List<StudentTermAssignment> overdueAssignments = termAssignments.stream()
                .filter(ta -> ta.getTermFeeStatus() == StudentTermAssignment.FeeStatus.OVERDUE)
                .collect(Collectors.toList());

        List<Map<String, Object>> upcomingDueDates = termAssignments.stream()
                .filter(ta -> ta.getDueDate() != null &&
                        ta.getDueDate().isAfter(LocalDate.now()) &&
                        ta.getPendingAmount() > 0)
                .sorted((a, b) -> a.getDueDate().compareTo(b.getDueDate()))
                .limit(5)
                .map(ta -> {
                    Map<String, Object> dueInfo = new HashMap<>();
                    dueInfo.put("termName", ta.getAcademicTerm().getTermName());
                    dueInfo.put("dueDate", ta.getDueDate());
                    dueInfo.put("amount", ta.getPendingAmount());
                    dueInfo.put("status", ta.getTermFeeStatus().name());
                    return dueInfo;
                })
                .collect(Collectors.toList());

        List<PaymentTransaction> paymentHistory = paymentTransactionRepository
                .findByStudentIdOrderByPaymentDateDesc(studentId);

        breakdown.put("studentId", studentId);
        breakdown.put("studentName", student.getFullName());
        breakdown.put("grade", student.getGrade());
        breakdown.put("studentCode", student.getStudentId());

        Map<String, Object> feeSummary = new HashMap<>();
        feeSummary.put("totalFee", totalTermFee);
        feeSummary.put("totalPaid", totalPaid);
        feeSummary.put("totalPending", totalPending);
        feeSummary.put("paymentPercentage", totalTermFee > 0 ? (totalPaid / totalTermFee) * 100 : 0);
        feeSummary.put("overdueCount", overdueAssignments.size());
        feeSummary.put("overdueAmount", overdueAssignments.stream()
                .mapToDouble(StudentTermAssignment::getPendingAmount)
                .sum());
        breakdown.put("feeSummary", feeSummary);

        if (currentAssignment.isPresent()) {
            Map<String, Object> currentTerm = new HashMap<>();
            StudentTermAssignment assignment = currentAssignment.get();
            currentTerm.put("termName", assignment.getAcademicTerm().getTermName());
            currentTerm.put("totalFee", assignment.getTotalTermFee());
            currentTerm.put("paidAmount", assignment.getPaidAmount());
            currentTerm.put("pendingAmount", assignment.getPendingAmount());
            currentTerm.put("status", assignment.getTermFeeStatus().name());
            currentTerm.put("dueDate", assignment.getDueDate());
            breakdown.put("currentTerm", currentTerm);
        }

        List<Map<String, Object>> termSummaries = termAssignments.stream()
                .map(ta -> {
                    Map<String, Object> summary = new HashMap<>();
                    summary.put("termName", ta.getAcademicTerm().getTermName());
                    summary.put("academicYear", ta.getAcademicTerm().getAcademicYear());
                    summary.put("totalFee", ta.getTotalTermFee());
                    summary.put("paidAmount", ta.getPaidAmount());
                    summary.put("pendingAmount", ta.getPendingAmount());
                    summary.put("status", ta.getTermFeeStatus().name());
                    summary.put("dueDate", ta.getDueDate());
                    summary.put("billingDate", ta.getBillingDate());
                    return summary;
                })
                .collect(Collectors.toList());
        breakdown.put("termSummaries", termSummaries);

        breakdown.put("upcomingDueDates", upcomingDueDates);

        List<Map<String, Object>> recentPayments = paymentHistory.stream()
                .limit(10)
                .map(pt -> {
                    Map<String, Object> payment = new HashMap<>();
                    payment.put("receiptNumber", pt.getReceiptNumber());
                    payment.put("amount", pt.getAmount());
                    payment.put("paymentDate", pt.getPaymentDate());
                    payment.put("paymentMethod", pt.getPaymentMethod().name());
                    payment.put("verified", pt.getIsVerified());
                    return payment;
                })
                .collect(Collectors.toList());
        breakdown.put("recentPayments", recentPayments);

        List<Map<String, Object>> feeAssignmentSummaries = feeAssignments.stream()
                .map(fa -> {
                    Map<String, Object> summary = new HashMap<>();
                    summary.put("academicYear", fa.getAcademicYear());
                    summary.put("totalAmount", fa.getTotalAmount());
                    summary.put("paidAmount", fa.getPaidAmount());
                    summary.put("pendingAmount", fa.getPendingAmount());
                    summary.put("status", fa.getFeeStatus().name());
                    summary.put("dueDate", fa.getDueDate());
                    return summary;
                })
                .collect(Collectors.toList());
        breakdown.put("feeAssignments", feeAssignmentSummaries);

        return breakdown;
    }

    public Map<String, Object> applyManualPayment(Long studentId, Double amount, String reference, String notes) {
        Map<String, Object> response = new HashMap<>();

        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            PaymentApplicationRequest feeRequest = new PaymentApplicationRequest();
            feeRequest.setStudentId(studentId);
            feeRequest.setAmount(amount);
            feeRequest.setReference(reference);
            feeRequest.setNotes(notes != null ? notes : "Manual payment");

            PaymentApplicationResponse paymentResult = termFeeService.applyPaymentToStudent(feeRequest);

            PaymentTransaction manualPayment = PaymentTransaction.builder()
                    .student(student)
                    .amount(amount)
                    .paymentMethod(com.system.SchoolManagementSystem.transaction.enums.PaymentMethod.CASH)
                    .paymentDate(LocalDateTime.now())
                    .receiptNumber("MANUAL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .isVerified(true)
                    .verifiedAt(LocalDateTime.now())
                    .notes(notes)
                    .paymentFor("MANUAL_PAYMENT")
                    .build();

            paymentTransactionRepository.save(manualPayment);

            response.put("success", true);
            response.put("message", String.format("Payment of KES %.2f applied to %s", amount, student.getFullName()));
            response.put("receiptNumber", manualPayment.getReceiptNumber());
            response.put("paymentResult", paymentResult);
            response.put("studentName", student.getFullName());
            response.put("appliedAmount", paymentResult.getAppliedPayment());
            response.put("remainingPayment", paymentResult.getRemainingPayment());
            response.put("allPaid", paymentResult.getAllPaid());

            log.info("✅ Manual payment applied: {} +KES {} (Receipt: {}), All paid: {}",
                    student.getFullName(), amount, manualPayment.getReceiptNumber(), paymentResult.getAllPaid());

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to apply manual payment: " + e.getMessage());
            log.error("❌ Failed to apply manual payment: {}", e.getMessage(), e);
        }

        return response;
    }

    public Map<String, Object> getFeeStatisticsByGrade() {
        Map<String, Object> statistics = new HashMap<>();

        Map<String, List<Student>> studentsByGrade = studentRepository.findAll().stream()
                .filter(s -> s.getStatus() == Student.StudentStatus.ACTIVE)
                .collect(Collectors.groupingBy(Student::getGrade));

        List<Map<String, Object>> gradeStats = new ArrayList<>();

        for (Map.Entry<String, List<Student>> entry : studentsByGrade.entrySet()) {
            String grade = entry.getKey();
            List<Student> students = entry.getValue();

            double totalFee = 0.0;
            double totalPaid = 0.0;
            double totalPending = 0.0;
            int overdueCount = 0;

            for (Student student : students) {
                totalFee += student.getTotalFee() != null ? student.getTotalFee() : 0.0;
                totalPaid += student.getPaidAmount() != null ? student.getPaidAmount() : 0.0;
                totalPending += student.getPendingAmount() != null ? student.getPendingAmount() : 0.0;

                if (student.getFeeStatus() == Student.FeeStatus.OVERDUE) {
                    overdueCount++;
                }
            }

            Map<String, Object> gradeStat = new HashMap<>();
            gradeStat.put("grade", grade);
            gradeStat.put("studentCount", students.size());
            gradeStat.put("totalFee", totalFee);
            gradeStat.put("totalPaid", totalPaid);
            gradeStat.put("totalPending", totalPending);
            gradeStat.put("overdueCount", overdueCount);
            gradeStat.put("collectionRate", totalFee > 0 ? (totalPaid / totalFee) * 100 : 0);

            gradeStats.add(gradeStat);
        }

        gradeStats.sort((a, b) -> ((String) a.get("grade")).compareTo((String) b.get("grade")));

        statistics.put("gradeStatistics", gradeStats);
        statistics.put("totalStudents", studentRepository.count());
        statistics.put("totalActiveStudents", studentRepository.findAll().stream()
                .filter(s -> s.getStatus() == Student.StudentStatus.ACTIVE)
                .count());

        double overallTotalFee = gradeStats.stream()
                .mapToDouble(gs -> (Double) gs.get("totalFee"))
                .sum();
        double overallTotalPaid = gradeStats.stream()
                .mapToDouble(gs -> (Double) gs.get("totalPaid"))
                .sum();
        double overallTotalPending = gradeStats.stream()
                .mapToDouble(gs -> (Double) gs.get("totalPending"))
                .sum();
        int overallOverdueCount = gradeStats.stream()
                .mapToInt(gs -> (Integer) gs.get("overdueCount"))
                .sum();

        statistics.put("overallTotalFee", overallTotalFee);
        statistics.put("overallTotalPaid", overallTotalPaid);
        statistics.put("overallTotalPending", overallTotalPending);
        statistics.put("overallOverdueCount", overallOverdueCount);
        statistics.put("overallCollectionRate", overallTotalFee > 0 ?
                (overallTotalPaid / overallTotalFee) * 100 : 0);

        return statistics;
    }

    // ========== STATISTICS OPERATIONS ==========

    public TransactionStatisticsResponse getTransactionStatistics() {
        TransactionStatisticsResponse statistics = new TransactionStatisticsResponse();

        try {
            long totalBank = bankTransactionRepository.count();
            long unverifiedCount = bankTransactionRepository.countByStatus(TransactionStatus.UNVERIFIED);
            long matchedCount = bankTransactionRepository.countByStatus(TransactionStatus.MATCHED);
            long verifiedCount = paymentTransactionRepository.countByIsVerified(true);

            statistics.setUnverifiedCount(unverifiedCount);
            statistics.setMatchedCount(matchedCount);
            statistics.setVerifiedCount(verifiedCount);

            if (totalBank > 0) {
                double matchRate = (matchedCount * 100.0) / totalBank;
                statistics.setMatchRate(String.format("%.1f%%", matchRate));
            } else {
                statistics.setMatchRate("0%");
            }

            Double totalAmount = paymentTransactionRepository.getTotalVerifiedAmount();
            statistics.setTotalAmount(totalAmount != null ? totalAmount : 0.0);

            Double todayAmount = paymentTransactionRepository.getTotalVerifiedAmountToday();
            statistics.setTodayAmount(todayAmount != null ? todayAmount : 0.0);

            Map<String, Object> feeStats = getFeeStatisticsByGrade();
            statistics.setFeeStatistics(feeStats);

            List<StudentTermAssignment> pendingAssignments = studentTermAssignmentRepository
                    .findAll().stream()
                    .filter(a -> a.getTermFeeStatus() == StudentTermAssignment.FeeStatus.PENDING ||
                            a.getTermFeeStatus() == StudentTermAssignment.FeeStatus.PARTIAL ||
                            a.getTermFeeStatus() == StudentTermAssignment.FeeStatus.OVERDUE)
                    .collect(Collectors.toList());

            long pendingPayments = pendingAssignments.stream()
                    .filter(a -> a.getTermFeeStatus() == StudentTermAssignment.FeeStatus.PENDING)
                    .count();

            long overduePayments = pendingAssignments.stream()
                    .filter(a -> a.getTermFeeStatus() == StudentTermAssignment.FeeStatus.OVERDUE)
                    .count();

            statistics.setPendingPayments(pendingPayments);
            statistics.setOverduePayments(overduePayments);

            double totalPendingAmount = pendingAssignments.stream()
                    .mapToDouble(StudentTermAssignment::getPendingAmount)
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
            List<BankTransaction> unverifiedTransactions = bankTransactionRepository
                    .findByTransactionDateBetween(startDate, endDate)
                    .stream()
                    .filter(t -> t.getStatus() == TransactionStatus.UNVERIFIED)
                    .collect(Collectors.toList());
            statistics.setUnverifiedCount((long) unverifiedTransactions.size());

            List<PaymentTransaction> verifiedPayments = paymentTransactionRepository
                    .findByPaymentDateBetween(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay())
                    .stream()
                    .filter(PaymentTransaction::getIsVerified)
                    .collect(Collectors.toList());
            statistics.setVerifiedCount((long) verifiedPayments.size());

            Double totalAmount = verifiedPayments.stream()
                    .mapToDouble(PaymentTransaction::getAmount)
                    .sum();
            statistics.setTotalAmount(totalAmount);

            if (LocalDate.now().isAfter(startDate.minusDays(1)) && LocalDate.now().isBefore(endDate.plusDays(1))) {
                Double todayAmount = paymentTransactionRepository.getTotalVerifiedAmountToday();
                statistics.setTodayAmount(todayAmount != null ? todayAmount : 0.0);
            } else {
                statistics.setTodayAmount(0.0);
            }

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

        String recipientPhone = request.getRecipientPhone();
        if (recipientPhone == null || recipientPhone.trim().isEmpty()) {
            recipientPhone = getBestContactPhone(student);
        }

        if (recipientPhone == null || recipientPhone.trim().isEmpty()) {
            log.warn("📵 No valid phone number available for student {}", student.getFullName());

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

        SmsLog smsLog = SmsLog.builder()
                .student(student)
                .paymentTransaction(paymentTransaction)
                .recipientPhone(recipientPhone)
                .message(request.getMessage())
                .status(SmsLog.SmsStatus.SENT)
                .gatewayMessageId("SMS-" + UUID.randomUUID().toString().substring(0, 8))
                .sentAt(LocalDateTime.now())
                .build();

        log.info("📲 SMS SENT: To {} - Message: {}", recipientPhone, request.getMessage());

        SmsLog savedSmsLog = smsLogRepository.save(smsLog);

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
                    .limit(100000)
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
                    .limit(100000)
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

        Optional<PaymentTransaction> paymentTransactionOpt = paymentTransactionRepository.findById(transactionId);
        if (paymentTransactionOpt.isPresent()) {
            PaymentTransaction paymentTransaction = paymentTransactionOpt.get();
            log.info("✅ Found payment transaction: {}", paymentTransaction.getReceiptNumber());

            if (!paymentTransaction.getIsVerified() && paymentTransaction.getBankTransaction() == null) {
                throw new RuntimeException("Payment transaction must be verified to generate receipt");
            }

            return receiptGenerator.generateReceiptPdf(paymentTransaction);
        }

        Optional<BankTransaction> bankTransactionOpt = bankTransactionRepository.findById(transactionId);
        if (bankTransactionOpt.isPresent()) {
            BankTransaction bankTransaction = bankTransactionOpt.get();
            log.info("✅ Found bank transaction: {}", bankTransaction.getBankReference());

            if (bankTransaction.getStatus() == TransactionStatus.MATCHED ||
                    bankTransaction.getStatus() == TransactionStatus.VERIFIED) {

                if (bankTransaction.getStudent() == null) {
                    throw new RuntimeException("Matched bank transaction must have a student assigned");
                }

                if (bankTransaction.getPaymentTransaction() == null) {
                    log.warn("⚠️ No payment transaction found for matched bank transaction, creating one...");
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

        log.error("❌ Transaction not found with ID: {}", transactionId);
        throw new RuntimeException("Transaction not found with id: " + transactionId);
    }

    // ========== HELPER METHODS ==========

    private String getBestContactPhone(Student student) {
        if (student == null) return null;

        if (student.getPhone() != null && !student.getPhone().trim().isEmpty()) {
            String phone = student.getPhone().trim();
            if (isValidIndianPhoneNumber(phone)) {
                return phone;
            }
        }

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

        String cleaned = phone.replaceAll("[^\\d+]", "");

        if (cleaned.startsWith("+91") && cleaned.length() == 13) {
            return cleaned;
        } else if (cleaned.startsWith("91") && cleaned.length() == 12) {
            return "+" + cleaned;
        } else if (cleaned.length() == 10) {
            return "+91" + cleaned;
        } else if (cleaned.length() > 10) {
            return "+91" + cleaned.substring(cleaned.length() - 10);
        }

        return cleaned;
    }

    private boolean isValidIndianPhoneNumber(String phone) {
        if (phone == null) return false;

        String cleaned = phone.replaceAll("[^\\d]", "");

        if (cleaned.matches("^[6-9]\\d{9}$")) {
            return true;
        }

        if (cleaned.matches("^91[6-9]\\d{9}$")) {
            return true;
        }

        return false;
    }

    // ========== CONVERSION METHODS ==========

    private BankTransactionResponse convertToBankTransactionResponseWithTermData(
            BankTransaction transaction,
            Map<Long, Boolean> hasAssignmentsMap,
            Map<Long, Integer> assignmentCountMap) {

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

        response.setSmsSent(transaction.getSmsSent());
        response.setSmsSentAt(transaction.getSmsSentAt());
        response.setSmsId(transaction.getSmsId());

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

            response.setStudentPendingAmount(student.getPendingAmount());
            response.setStudentFeeStatus(student.getFeeStatus());
            response.setStudentTotalFee(student.getTotalFee());
            response.setStudentPaidAmount(student.getPaidAmount());

            // ========== USE PRE-FETCHED TERM ASSIGNMENT DATA ==========
            Long studentId = student.getId();
            Boolean hasAssignments = hasAssignmentsMap.get(studentId);
            Integer assignmentCount = assignmentCountMap.get(studentId);

            response.setHasTermAssignments(hasAssignments != null ? hasAssignments : false);
            response.setTermAssignmentCount(assignmentCount != null ? assignmentCount : 0);
            // ==========================================================

            if (student.getPendingAmount() != null && student.getTotalFee() != null &&
                    student.getTotalFee() > 0) {
                double percentage = (student.getPaidAmount() != null ? student.getPaidAmount() : 0.0) /
                        student.getTotalFee() * 100;
                response.setStudentPaymentPercentage(percentage);
            }
        }

        return response;
    }

    private BankTransactionResponse convertToBankTransactionResponse(BankTransaction transaction) {
        return convertToBankTransactionResponseWithTermData(transaction, new HashMap<>(), new HashMap<>());
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

            response.setStudentPendingAmount(student.getPendingAmount());
            response.setStudentFeeStatus(student.getFeeStatus());
            response.setStudentTotalFee(student.getTotalFee());
            response.setStudentPaidAmount(student.getPaidAmount());

            // ========== QUERY TERM ASSIGNMENTS FOR PAYMENT TRANSACTIONS ==========
            try {
                boolean hasAssignments = studentTermAssignmentRepository.hasTermAssignments(student.getId());
                Integer assignmentCount = studentTermAssignmentRepository.countTermAssignments(student.getId());

                response.setHasTermAssignments(hasAssignments);
                response.setTermAssignmentCount(assignmentCount != null ? assignmentCount : 0);

            } catch (Exception e) {
                log.warn("Error querying term assignments for student {}: {}",
                        student.getId(), e.getMessage());
                response.setHasTermAssignments(false);
                response.setTermAssignmentCount(0);
            }
            // ==========================================================

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
            Page<BankTransaction> searchResult = bankTransactionRepository.searchTransactions(
                    search, Pageable.unpaged());
            transactions = searchResult.getContent().stream()
                    .filter(t -> t.getStatus() == status)
                    .collect(Collectors.toList());

        } else if (status != null) {
            transactions = bankTransactionRepository.findByStatus(status);

        } else if (search != null && !search.trim().isEmpty()) {
            Page<BankTransaction> searchResult = bankTransactionRepository.searchTransactions(
                    search, Pageable.unpaged());
            transactions = searchResult.getContent();

        } else {
            transactions = bankTransactionRepository.findAll();
        }

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

        transactions.sort((a, b) -> b.getTransactionDate().compareTo(a.getTransactionDate()));

        return transactions.stream()
                .map(this::convertToBankTransactionResponse)
                .collect(Collectors.toList());
    }

    public Page<BankTransactionResponse> getBankTransactions(
            TransactionStatus status, String search, LocalDate fromDate, LocalDate toDate, Pageable pageable) {

        Page<BankTransaction> transactions;

        if (status != null && search != null && !search.trim().isEmpty()) {
            Page<BankTransaction> searchResult = bankTransactionRepository.searchTransactions(search, pageable);
            List<BankTransaction> filtered = searchResult.getContent().stream()
                    .filter(t -> t.getStatus() == status)
                    .collect(Collectors.toList());

            transactions = new PageImpl<>(filtered, pageable, filtered.size());

        } else if (status != null) {
            transactions = bankTransactionRepository.findByStatus(status, pageable);

        } else if (search != null && !search.trim().isEmpty()) {
            transactions = bankTransactionRepository.searchTransactions(search, pageable);

        } else {
            transactions = bankTransactionRepository.findAll(pageable);
        }

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
            transactions = paymentTransactionRepository.findByIsVerified(true);
        }

        transactions.sort((a, b) -> b.getPaymentDate().compareTo(a.getPaymentDate()));

        return transactions.stream()
                .map(this::convertToPaymentTransactionResponse)
                .collect(Collectors.toList());
    }

    public long getTotalBankTransactionCount() {
        return bankTransactionRepository.count();
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
        List<StudentTermAssignment> allAssignments = studentTermAssignmentRepository.findAll();
        return allAssignments.stream()
                .mapToDouble(StudentTermAssignment::getPendingAmount)
                .sum();
    }

    public Double getTotalVerifiedAmount() {
        try {
            Double amount = paymentTransactionRepository.getTotalVerifiedAmount();
            return amount != null ? amount : 0.0;
        } catch (Exception e) {
            log.error("Error getting total verified amount", e);
            return 0.0;
        }
    }

    public Double getTotalVerifiedAmountToday() {
        try {
            Double amount = paymentTransactionRepository.getTotalVerifiedAmountToday();
            return amount != null ? amount : 0.0;
        } catch (Exception e) {
            log.error("Error getting today's verified amount", e);
            return 0.0;
        }
    }

    // ========== NEW METHODS FOR CONTROLLER ENDPOINTS ==========

    public Map<String, Object> getStudentFeeDetails(Long studentId) {
        return getStudentFeeBreakdown(studentId);
    }

    public Map<String, Object> applyManualPaymentToStudent(Long studentId, Double amount,
                                                           String reference, String notes) {
        return applyManualPayment(studentId, amount, reference, notes);
    }

    public Map<String, Object> getFeeStatistics() {
        return getFeeStatisticsByGrade();
    }
    public Student getStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + studentId));
    }
}