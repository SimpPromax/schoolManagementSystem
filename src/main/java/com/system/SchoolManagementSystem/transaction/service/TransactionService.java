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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    // Repositories
    private final BankTransactionRepository bankTransactionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final StudentFeeAssignmentRepository feeAssignmentRepository;
    private final FeeInstallmentRepository feeInstallmentRepository;
    private final SmsLogRepository smsLogRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    // Utilities
    private final ReceiptGenerator receiptGenerator;
    private final TransactionMatcher transactionMatcher;
    private final BankStatementParser bankStatementParser;

    // ========== Bank Transaction Operations ==========

    public List<BankTransactionResponse> importBankTransactions(BankTransactionImportRequest request) {
        log.info("Importing bank transactions from file: {}", request.getFile().getOriginalFilename());

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

            // Get all students for auto-matching
            List<Student> allStudents = studentRepository.findAll();
            log.info("Loaded {} students for auto-matching", allStudents.size());

            // Auto-match transactions to students
            List<BankTransaction> matchedTransactions = autoMatchTransactions(transactions, allStudents);

            // Save transactions
            List<BankTransaction> savedTransactions = bankTransactionRepository.saveAll(matchedTransactions);

            long autoMatchedCount = savedTransactions.stream()
                    .filter(bt -> bt.getStudent() != null)
                    .count();

            log.info("Successfully imported {} transactions, {} auto-matched",
                    savedTransactions.size(),
                    autoMatchedCount);

            // Convert to response DTOs
            return savedTransactions.stream()
                    .map(this::convertToBankTransactionResponse)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to import bank transactions", e);
            throw new RuntimeException("Failed to import transactions: " + e.getMessage());
        }
    }

    private List<BankTransaction> autoMatchTransactions(List<BankTransaction> transactions, List<Student> students) {
        List<BankTransaction> matchedTransactions = new ArrayList<>();

        for (BankTransaction transaction : transactions) {
            try {
                Optional<Student> matchedStudent = transactionMatcher.findMatchingStudent(transaction, students);

                if (matchedStudent.isPresent()) {
                    Student student = matchedStudent.get();

                    // Calculate match score
                    Double matchScore = transactionMatcher.calculateMatchScore(
                            transaction,
                            student,
                            getStudentPendingAmount(student) // Get pending amount
                    );

                    // Only auto-match if score is high enough (e.g., > 50)
                    if (matchScore > 50.0) {
                        transaction.setStudent(student);
                        transaction.setStatus(TransactionStatus.MATCHED);
                        transaction.setMatchedAt(LocalDateTime.now());
                        log.info("Auto-matched transaction '{}' (₹{}) to student '{}' with score {}",
                                transaction.getDescription(),
                                transaction.getAmount(),
                                student.getFullName(),
                                matchScore);
                    } else {
                        log.debug("Transaction '{}' matched to student '{}' but score too low: {}",
                                transaction.getDescription(),
                                student.getFullName(),
                                matchScore);
                    }
                }

                matchedTransactions.add(transaction);
            } catch (Exception e) {
                log.error("Error auto-matching transaction: {}", transaction.getBankReference(), e);
                matchedTransactions.add(transaction);
            }
        }

        return matchedTransactions;
    }

    private Double getStudentPendingAmount(Student student) {
        // Try to get pending amount from student DTO or calculate from fee assignments
        try {
            // If student has a pendingAmount field (from your StudentDTO)
            // This depends on your Student entity structure
            return 0.0; // Placeholder - update based on your Student entity
        } catch (Exception e) {
            log.debug("Could not get pending amount for student {}", student.getFullName());
            return null;
        }
    }

    public Page<BankTransactionResponse> getBankTransactions(TransactionStatus status, String search, Pageable pageable) {
        Page<BankTransaction> transactions;

        if (status != null && search != null && !search.trim().isEmpty()) {
            // Filter by both status and search
            transactions = bankTransactionRepository.findByStatus(status, pageable);
            // Further filter by search in memory
            return transactions.map(this::convertToBankTransactionResponse);
        } else if (status != null) {
            transactions = bankTransactionRepository.findByStatus(status, pageable);
        } else if (search != null && !search.trim().isEmpty()) {
            transactions = bankTransactionRepository.searchTransactions(search, pageable);
        } else {
            transactions = bankTransactionRepository.findAll(pageable);
        }

        return transactions.map(this::convertToBankTransactionResponse);
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

        BankTransaction savedTransaction = bankTransactionRepository.save(transaction);
        log.info("Manually matched transaction {} to student {}", transactionId, student.getFullName());
        return convertToBankTransactionResponse(savedTransaction);
    }

    public void deleteBankTransaction(Long id) {
        if (!bankTransactionRepository.existsById(id)) {
            throw new RuntimeException("Transaction not found with id: " + id);
        }
        bankTransactionRepository.deleteById(id);
        log.info("Deleted bank transaction with id: {}", id);
    }

    // ========== Payment Transaction Operations ==========

    public PaymentTransactionResponse verifyPayment(PaymentVerificationRequest request) {
        log.info("Verifying payment for bank transaction: {}", request.getBankTransactionId());

        // Find bank transaction
        BankTransaction bankTransaction = bankTransactionRepository.findById(request.getBankTransactionId())
                .orElseThrow(() -> new RuntimeException("Bank transaction not found"));

        // Find student
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // Create payment transaction
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .student(student)
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .paymentDate(LocalDateTime.now())
                .bankTransaction(bankTransaction)
                .bankReference(bankTransaction.getBankReference())
                .isVerified(true)
                .verifiedAt(LocalDateTime.now())
                .smsSent(false) // Explicitly set to false
                .smsSentAt(null)
                .smsId(null)
                .paymentFor(request.getPaymentFor())
                .discountApplied(request.getDiscountApplied())
                .lateFeePaid(request.getLateFeePaid())
                .convenienceFee(request.getConvenienceFee())
                .notes(request.getNotes())
                .build();

        // Update bank transaction status
        bankTransaction.setStatus(TransactionStatus.VERIFIED);
        bankTransactionRepository.save(bankTransaction);

        // Save payment transaction
        PaymentTransaction savedTransaction = paymentTransactionRepository.save(paymentTransaction);
        log.info("Payment verified: Receipt {} for student {} - ₹{}",
                savedTransaction.getReceiptNumber(),
                student.getFullName(),
                savedTransaction.getAmount());

        // Send SMS if requested
        if (request.getSendSms() != null && request.getSendSms()) {
            try {
                SmsRequest smsRequest = new SmsRequest();
                smsRequest.setStudentId(student.getId());
                smsRequest.setPaymentTransactionId(savedTransaction.getId());
                smsRequest.setMessage("Payment of ₹" + request.getAmount() + " received. Receipt: " + savedTransaction.getReceiptNumber());

                // Get student phone or emergency contact phone
                String recipientPhone = getBestContactPhone(student);
                if (recipientPhone != null && !recipientPhone.trim().isEmpty()) {
                    smsRequest.setRecipientPhone(recipientPhone);
                    sendPaymentSms(smsRequest);
                    log.info("SMS sent for payment {}", savedTransaction.getReceiptNumber());
                } else {
                    log.warn("No phone number available for student {} to send SMS", student.getFullName());
                }
            } catch (Exception e) {
                log.warn("Failed to send SMS for payment {}: {}", savedTransaction.getId(), e.getMessage());
                // Don't fail the whole transaction if SMS fails
            }
        }

        return convertToPaymentTransactionResponse(savedTransaction);
    }

    public List<PaymentTransactionResponse> bulkVerifyPayments(BulkVerificationRequest request) {
        log.info("Bulk verifying {} payments", request.getBankTransactionIds().size());

        List<PaymentTransactionResponse> responses = new ArrayList<>();

        for (Long bankTransactionId : request.getBankTransactionIds()) {
            try {
                BankTransaction bankTransaction = bankTransactionRepository.findById(bankTransactionId)
                        .orElseThrow(() -> new RuntimeException("Bank transaction not found: " + bankTransactionId));

                // Check if transaction is already matched to a student
                if (bankTransaction.getStudent() == null) {
                    log.warn("Skipping transaction {} - not matched to any student", bankTransactionId);
                    continue;
                }

                // Create payment verification request
                PaymentVerificationRequest singleRequest = new PaymentVerificationRequest();
                singleRequest.setBankTransactionId(bankTransactionId);
                singleRequest.setStudentId(bankTransaction.getStudent().getId());
                singleRequest.setAmount(bankTransaction.getAmount());
                singleRequest.setPaymentMethod(bankTransaction.getPaymentMethod());
                singleRequest.setSendSms(request.getSendSms());
                singleRequest.setNotes(request.getNotes());

                // Verify the payment
                PaymentTransactionResponse verifiedPayment = verifyPayment(singleRequest);
                responses.add(verifiedPayment);

                log.info("Bulk verified transaction {} for student {}",
                        bankTransactionId,
                        bankTransaction.getStudent().getFullName());

            } catch (Exception e) {
                log.error("Failed to verify payment for transaction {}: {}", bankTransactionId, e.getMessage());
            }
        }

        log.info("Bulk verification completed: {} successful, {} total",
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
            log.error("Error getting verified transactions", e);
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

    // ========== Statistics Operations ==========

    public TransactionStatisticsResponse getTransactionStatistics() {
        TransactionStatisticsResponse statistics = new TransactionStatisticsResponse();

        try {
            // Get ALL bank transactions
            List<BankTransaction> allBankTransactions = bankTransactionRepository.findAll();

            // Manual counts for accuracy
            long totalBank = allBankTransactions.size();
            long unverifiedCount = 0;
            long matchedCount = 0;
            long bankVerifiedCount = 0;

            for (BankTransaction tx : allBankTransactions) {
                if (tx.getStatus() == TransactionStatus.UNVERIFIED) {
                    unverifiedCount++;
                } else if (tx.getStatus() == TransactionStatus.MATCHED) {
                    matchedCount++;
                } else if (tx.getStatus() == TransactionStatus.VERIFIED) {
                    bankVerifiedCount++;
                }
            }

            // Get verified payment transactions count
            List<PaymentTransaction> allPaymentTransactions = paymentTransactionRepository.findAll();
            long paymentVerifiedCount = allPaymentTransactions.stream()
                    .filter(PaymentTransaction::getIsVerified)
                    .count();

            // Set counts
            statistics.setUnverifiedCount(unverifiedCount); // For "Unmatched Bank Transactions"
            statistics.setMatchedCount(matchedCount); // For "Matched Bank Transactions"
            statistics.setVerifiedCount(paymentVerifiedCount); // For "Verified Payments"

            // Calculate match rate: Matched Bank Transactions / Total Bank Transactions
            // This shows auto-matching success rate
            if (totalBank > 0) {
                double matchRate = (matchedCount * 100.0) / totalBank;
                statistics.setMatchRate(String.format("%.1f%%", matchRate));

                log.info("✅ MATCH RATE CALCULATION:");
                log.info("   Total Bank Transactions: {}", totalBank);
                log.info("   Matched (Auto-matched): {}", matchedCount);
                log.info("   Unverified (Unmatched): {}", unverifiedCount);
                log.info("   Bank Verified: {}", bankVerifiedCount);
                log.info("   Payment Verified: {}", paymentVerifiedCount);
                log.info("   Match Rate = ({} / {}) × 100 = {}%", matchedCount, totalBank, matchRate);
            } else {
                statistics.setMatchRate("0%");
                log.info("No bank transactions found, match rate set to 0%");
            }

            // Calculate total verified amount from payment transactions
            double totalAmount = allPaymentTransactions.stream()
                    .filter(PaymentTransaction::getIsVerified)
                    .mapToDouble(PaymentTransaction::getAmount)
                    .sum();
            statistics.setTotalAmount(totalAmount);

            // Calculate today's verified amount
            double todayAmount = allPaymentTransactions.stream()
                    .filter(PaymentTransaction::getIsVerified)
                    .filter(pt -> pt.getPaymentDate() != null)
                    .filter(pt -> pt.getPaymentDate().toLocalDate().equals(LocalDate.now()))
                    .mapToDouble(PaymentTransaction::getAmount)
                    .sum();
            statistics.setTodayAmount(todayAmount);

            // Get pending and overdue payments from fee assignments
            List<StudentFeeAssignment> allAssignments = feeAssignmentRepository.findAll();

            long pendingPayments = allAssignments.stream()
                    .filter(a -> a.getFeeStatus() == com.system.SchoolManagementSystem.transaction.enums.FeeStatus.PENDING)
                    .count();

            long overduePayments = allAssignments.stream()
                    .filter(a -> a.getFeeStatus() == com.system.SchoolManagementSystem.transaction.enums.FeeStatus.OVERDUE)
                    .count();

            statistics.setPendingPayments(pendingPayments);
            statistics.setOverduePayments(overduePayments);

            // Calculate total pending amount
            double totalPendingAmount = allAssignments.stream()
                    .mapToDouble(StudentFeeAssignment::getPendingAmount)
                    .sum();
            statistics.setTotalPendingAmount(totalPendingAmount);

            // Log final statistics
            log.info("📊 FINAL TRANSACTION STATISTICS:");
            log.info("   Match Rate: {}", statistics.getMatchRate());
            log.info("   Unmatched Bank Transactions: {}", unverifiedCount);
            log.info("   Matched Bank Transactions: {}", matchedCount);
            log.info("   Verified Payments: {}", paymentVerifiedCount);
            log.info("   Total Verified Amount: ₹{}", statistics.getTotalAmount());
            log.info("   Today's Verified Amount: ₹{}", statistics.getTodayAmount());
            log.info("   Pending Fee Assignments: {}", pendingPayments);
            log.info("   Overdue Fee Assignments: {}", overduePayments);
            log.info("   Total Pending Amount: ₹{}", totalPendingAmount);

        } catch (Exception e) {
            log.error("❌ Error calculating transaction statistics", e);
            // Return default statistics with error indicator
            statistics.setMatchRate("Error");
        }

        return statistics;
    }

    public TransactionStatisticsResponse getStatisticsByDateRange(LocalDate startDate, LocalDate endDate) {
        TransactionStatisticsResponse statistics = new TransactionStatisticsResponse();

        // Implement date-range specific statistics
        // For now, returning basic filtered statistics

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

        return statistics;
    }

    // ========== SMS Operations ==========

    public SmsLogResponse sendPaymentSms(SmsRequest request) {
        log.info("Sending SMS for payment transaction: {}", request.getPaymentTransactionId());

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
            log.warn("No valid phone number available for student {}", student.getFullName());

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
            log.warn("Invalid phone number format for student {}: {}", student.getFullName(), recipientPhone);

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
        // For now, simulate successful sending
        log.info("SMS SENT: To {} - Message: {}", recipientPhone, request.getMessage());

        SmsLog savedSmsLog = smsLogRepository.save(smsLog);

        // Update payment transaction
        paymentTransaction.setSmsSent(true);
        paymentTransaction.setSmsSentAt(LocalDateTime.now());
        paymentTransaction.setSmsId(savedSmsLog.getGatewayMessageId());
        paymentTransactionRepository.save(paymentTransaction);

        log.info("SMS record saved with ID: {}", savedSmsLog.getId());
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

    // ========== Export Operations ==========

    public byte[] exportTransactionsToCsv(String type, LocalDate startDate, LocalDate endDate) {
        StringBuilder csvContent = new StringBuilder();

        if ("verified".equalsIgnoreCase(type)) {
            csvContent.append("Receipt Number,Payment Date,Amount,Student Name,Grade,Payment Method,Bank Reference\n");

            List<PaymentTransaction> transactions = paymentTransactionRepository
                    .findByPaymentDateBetween(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay())
                    .stream()
                    .filter(PaymentTransaction::getIsVerified)
                    .collect(Collectors.toList());

            for (PaymentTransaction transaction : transactions) {
                csvContent.append(String.format("\"%s\",%s,%.2f,\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        transaction.getReceiptNumber(),
                        transaction.getPaymentDate().toLocalDate(),
                        transaction.getAmount(),
                        transaction.getStudent().getFullName(),
                        transaction.getStudent().getGrade(),
                        transaction.getPaymentMethod(),
                        transaction.getBankReference() != null ? transaction.getBankReference() : ""
                ));
            }

            log.info("Exported {} verified transactions to CSV", transactions.size());

        } else if ("bank".equalsIgnoreCase(type)) {
            csvContent.append("Bank Reference,Transaction Date,Description,Amount,Status,Student Name,Bank Account\n");

            List<BankTransaction> transactions = bankTransactionRepository
                    .findByTransactionDateBetween(startDate, endDate);

            for (BankTransaction transaction : transactions) {
                csvContent.append(String.format("\"%s\",%s,\"%s\",%.2f,\"%s\",\"%s\",\"%s\"\n",
                        transaction.getBankReference(),
                        transaction.getTransactionDate(),
                        transaction.getDescription(),
                        transaction.getAmount(),
                        transaction.getStatus(),
                        transaction.getStudent() != null ? transaction.getStudent().getFullName() : "",
                        transaction.getBankAccount() != null ? transaction.getBankAccount() : ""
                ));
            }

            log.info("Exported {} bank transactions to CSV", transactions.size());
        } else {
            throw new IllegalArgumentException("Invalid export type: " + type);
        }

        return csvContent.toString().getBytes();
    }

    // In TransactionService.java
    // In TransactionService.java, update the generateReceiptPdf method:
    public byte[] generateReceiptPdf(Long transactionId) {
        log.info("🔍 Looking for transaction ID: {}", transactionId);

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

                log.info("📄 Generating receipt for auto-matched bank transaction");
                return receiptGenerator.generateReceiptPdf(bankTransaction);
            } else {
                throw new RuntimeException("Bank transaction must be MATCHED or VERIFIED to generate receipt");
            }
        }

        // Transaction not found
        log.error("❌ Transaction not found with ID: {}", transactionId);
        throw new RuntimeException("Transaction not found with id: " + transactionId);
    }

    // ========== Helper Methods ==========

    private String getBestContactPhone(Student student) {
        if (student == null) return null;

        // Try student's own phone first
        if (student.getPhone() != null && !student.getPhone().trim().isEmpty()) {
            return student.getPhone().trim();
        }

        // Try emergency contact phone
        if (student.getEmergencyContactPhone() != null && !student.getEmergencyContactPhone().trim().isEmpty()) {
            return student.getEmergencyContactPhone().trim();
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

    // ========== Conversion Methods ==========

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

        if (transaction.getStudent() != null) {
            response.setStudentId(transaction.getStudent().getId());
            response.setStudentName(transaction.getStudent().getFullName());
            response.setStudentGrade(transaction.getStudent().getGrade());
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
            response.setStudentId(transaction.getStudent().getId());
            response.setStudentName(transaction.getStudent().getFullName());
            response.setStudentGrade(transaction.getStudent().getGrade());
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
}