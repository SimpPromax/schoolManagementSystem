package com.system.SchoolManagementSystem.transaction.service;

import com.system.SchoolManagementSystem.transaction.dto.request.*;
import com.system.SchoolManagementSystem.transaction.dto.response.*;
import com.system.SchoolManagementSystem.transaction.enums.TransactionStatus;
import com.system.SchoolManagementSystem.transaction.entity.*;
import com.system.SchoolManagementSystem.transaction.repository.*;
import com.system.SchoolManagementSystem.transaction.util.*;
import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.auth.entity.User;
import com.system.SchoolManagementSystem.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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

            // Save transactions
            List<BankTransaction> savedTransactions = bankTransactionRepository.saveAll(transactions);
            log.info("Successfully imported {} transactions", savedTransactions.size());

            // Convert to response DTOs
            return savedTransactions.stream()
                    .map(this::convertToBankTransactionResponse)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to import bank transactions", e);
            throw new RuntimeException("Failed to import transactions: " + e.getMessage());
        }
    }

    public Page<BankTransactionResponse> getBankTransactions(TransactionStatus status, String search, Pageable pageable) {
        Page<BankTransaction> transactions;

        if (status != null && search != null && !search.trim().isEmpty()) {
            // Filter by both status and search
            transactions = bankTransactionRepository.findByStatus(status, pageable);
            // Further filter by search in memory (or create a custom repository method)
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

        // Send SMS if requested
        if (request.getSendSms()) {
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
                // For bulk verification, you might want a simplified verification process
                // or reuse the single verification logic
                BankTransaction bankTransaction = bankTransactionRepository.findById(bankTransactionId)
                        .orElseThrow(() -> new RuntimeException("Bank transaction not found: " + bankTransactionId));

                // Here you would need to determine which student to match with
                // This is a simplified example - you might need more logic

                // Create a simple payment verification request
                PaymentVerificationRequest singleRequest = new PaymentVerificationRequest();
                singleRequest.setBankTransactionId(bankTransactionId);
                singleRequest.setAmount(bankTransaction.getAmount());
                singleRequest.setPaymentMethod(bankTransaction.getPaymentMethod());
                singleRequest.setSendSms(request.getSendSms());
                singleRequest.setNotes(request.getNotes());

                // You need to set studentId somehow - this is a placeholder
                // In real implementation, you'd need to match students first
                // For now, we'll skip this transaction in bulk verification
                log.warn("Skipping bulk verification for transaction {} - student matching required", bankTransactionId);

            } catch (Exception e) {
                log.error("Failed to verify payment for transaction {}: {}", bankTransactionId, e.getMessage());
            }
        }

        return responses;
    }

    public Page<PaymentTransactionResponse> getVerifiedTransactions(String search, Pageable pageable) {
        Page<PaymentTransaction> transactions;

        if (search != null && !search.trim().isEmpty()) {
            transactions = paymentTransactionRepository.searchTransactions(search, pageable);
        } else {
            transactions = paymentTransactionRepository.findByIsVerified(true, pageable);
        }

        return transactions.map(this::convertToPaymentTransactionResponse);
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

        // Count unverified bank transactions
        Long unverifiedCount = bankTransactionRepository.countByStatus(TransactionStatus.UNVERIFIED);
        statistics.setUnverifiedCount(unverifiedCount != null ? unverifiedCount : 0L);

        // Count verified bank transactions
        Long verifiedCount = bankTransactionRepository.countByStatus(TransactionStatus.VERIFIED);
        statistics.setVerifiedCount(verifiedCount != null ? verifiedCount : 0L);

        // Get total verified amount
        Double totalAmount = paymentTransactionRepository.getTotalVerifiedAmount();
        statistics.setTotalAmount(totalAmount != null ? totalAmount : 0.0);

        // Get today's verified amount
        Double todayAmount = paymentTransactionRepository.getTotalVerifiedAmountToday();
        statistics.setTodayAmount(todayAmount != null ? todayAmount : 0.0);

        // Calculate match rate
        if (verifiedCount != null && unverifiedCount != null && (verifiedCount + unverifiedCount) > 0) {
            double matchRate = (verifiedCount.doubleValue() / (verifiedCount + unverifiedCount)) * 100;
            statistics.setMatchRate(String.format("%.1f%%", matchRate));
        } else {
            statistics.setMatchRate("0%");
        }

        // Get pending and overdue payments
        // You would need to implement these repository methods
        statistics.setPendingPayments(0L); // Placeholder
        statistics.setOverduePayments(0L); // Placeholder
        statistics.setTotalPendingAmount(0.0); // Placeholder

        return statistics;
    }

    public TransactionStatisticsResponse getStatisticsByDateRange(LocalDate startDate, LocalDate endDate) {
        // Similar to above but filtered by date range
        TransactionStatisticsResponse statistics = new TransactionStatisticsResponse();

        // You would need to add date-range specific repository methods
        // For now, returning basic statistics
        return getTransactionStatistics();
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
                .status(SmsLog.SmsStatus.SENT) // In real implementation, this would be QUEUED or PENDING
                .gatewayMessageId("SIMULATED-" + UUID.randomUUID().toString().substring(0, 8))
                .sentAt(LocalDateTime.now())
                .build();

        // In a real implementation, you would call an SMS gateway here
        // For now, we'll simulate successful sending
        log.info("SIMULATED: Sending SMS to {}: {}", recipientPhone, request.getMessage());

        SmsLog savedSmsLog = smsLogRepository.save(smsLog);

        // Update payment transaction
        paymentTransaction.setSmsSent(true);
        paymentTransaction.setSmsSentAt(LocalDateTime.now());
        paymentTransaction.setSmsId(savedSmsLog.getGatewayMessageId());
        paymentTransactionRepository.save(paymentTransaction);

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
        // Implementation for CSV export
        // This would generate CSV content based on type and date range
        StringBuilder csvContent = new StringBuilder();
        csvContent.append("Transaction ID,Date,Amount,Status,Student Name\n");

        if ("verified".equalsIgnoreCase(type)) {
            List<PaymentTransaction> transactions = paymentTransactionRepository.findAll();
            for (PaymentTransaction transaction : transactions) {
                if (transaction.getIsVerified()) {
                    csvContent.append(String.format("%s,%s,%.2f,%s,%s\n",
                            transaction.getReceiptNumber(),
                            transaction.getPaymentDate(),
                            transaction.getAmount(),
                            "VERIFIED",
                            transaction.getStudent().getFullName()));
                }
            }
        } else if ("bank".equalsIgnoreCase(type)) {
            List<BankTransaction> transactions = bankTransactionRepository.findAll();
            for (BankTransaction transaction : transactions) {
                csvContent.append(String.format("%s,%s,%.2f,%s,%s\n",
                        transaction.getBankReference(),
                        transaction.getTransactionDate(),
                        transaction.getAmount(),
                        transaction.getStatus(),
                        transaction.getStudent() != null ? transaction.getStudent().getFullName() : ""));
            }
        }

        return csvContent.toString().getBytes();
    }

    public byte[] generateReceiptPdf(Long paymentTransactionId) {
        PaymentTransaction paymentTransaction = paymentTransactionRepository.findById(paymentTransactionId)
                .orElseThrow(() -> new RuntimeException("Payment transaction not found"));

        return receiptGenerator.generateReceiptPdf(paymentTransaction);
    }

    // ========== Phone Number Helper Methods ==========

    /**
     * Get the best available contact phone for a student
     * Priority: 1. Student phone, 2. Emergency contact phone, 3. null
     */
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

    /**
     * Clean phone number by removing non-digit characters
     */
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

    /**
     * Validate Indian phone number
     */
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

    // ========== Helper Conversion Methods ==========

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