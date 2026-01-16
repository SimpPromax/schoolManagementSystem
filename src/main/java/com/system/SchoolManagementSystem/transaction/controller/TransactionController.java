package com.system.SchoolManagementSystem.transaction.controller;

import com.system.SchoolManagementSystem.transaction.dto.request.*;
import com.system.SchoolManagementSystem.transaction.dto.response.*;
import com.system.SchoolManagementSystem.transaction.enums.TransactionStatus;
import com.system.SchoolManagementSystem.transaction.service.TransactionService;
import com.system.SchoolManagementSystem.transaction.service.StudentCacheService;
import com.system.SchoolManagementSystem.transaction.util.TransactionMatcher;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;
    private final StudentCacheService studentCacheService;
    private final TransactionMatcher transactionMatcher;

    // ========== OPTIMIZATION ENDPOINTS ==========

    @GetMapping("/optimization/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        try {
            StudentCacheService.CacheStats stats = transactionService.getCacheStats();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cache statistics retrieved successfully");
            response.put("data", stats);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get cache stats: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/optimization/matcher/cache/status")
    public ResponseEntity<Map<String, Object>> getMatcherCacheStatus() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("cacheLoaded", transactionMatcher.isCacheLoaded());
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get cache status: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/optimization/cache/refresh")
    public ResponseEntity<Map<String, Object>> refreshAllCaches() {
        try {
            // Refresh both caches
            studentCacheService.refreshCache();
            transactionService.refreshMatcherCache();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "All caches refresh initiated");
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to refresh caches: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/optimization/matcher/cache/refresh")
    public ResponseEntity<Map<String, Object>> refreshMatcherCache() {
        try {
            transactionService.refreshMatcherCache();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Transaction matcher cache refresh initiated");
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to refresh matcher cache: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ========== BANK TRANSACTION ENDPOINTS (UPDATED) ==========

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importBankTransactions(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bankAccount", required = false) String bankAccount,
            @RequestParam(value = "importType", defaultValue = "CSV") String importType) {

        try {
            BankTransactionImportRequest request = new BankTransactionImportRequest();
            request.setFile(file);
            request.setBankAccount(bankAccount);
            request.setImportType(importType);
            request.setImportBatchId(java.util.UUID.randomUUID().toString());

            List<BankTransactionResponse> transactions = transactionService.importBankTransactions(request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Successfully imported " + transactions.size() + " transactions");
            response.put("data", transactions);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to import transactions: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/bank")
    public ResponseEntity<Map<String, Object>> getBankTransactions(
            @RequestParam(value = "status", required = false) TransactionStatus status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "all", defaultValue = "false") Boolean all,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 50) Pageable pageable) {

        try {
            if (Boolean.TRUE.equals(all)) {
                // ========== RETURN ALL TRANSACTIONS (NO PAGINATION) ==========
                log.info("Fetching ALL bank transactions (no pagination)");
                long startTime = System.currentTimeMillis();

                List<BankTransactionResponse> allTransactions = transactionService.getAllBankTransactions(
                        status, search, fromDate, toDate);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "All bank transactions retrieved successfully");
                response.put("data", allTransactions);
                response.put("total", allTransactions.size());
                response.put("timestamp", LocalDateTime.now().toString());
                response.put("paginationEnabled", false);
                response.put("processingTime", System.currentTimeMillis() - startTime);

                log.info("Returning ALL {} transactions (unpaginated) in {}ms",
                        allTransactions.size(), System.currentTimeMillis() - startTime);
                return ResponseEntity.ok(response);

            } else {
                // ========== ORIGINAL PAGINATED RESPONSE ==========
                log.info("Fetching paginated bank transactions (page {}, size {})",
                        pageable.getPageNumber(), pageable.getPageSize());

                Page<BankTransactionResponse> transactions = transactionService.getBankTransactions(
                        status, search, fromDate, toDate, pageable);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Bank transactions retrieved successfully");
                response.put("data", transactions.getContent());
                response.put("page", transactions.getNumber());
                response.put("size", transactions.getSize());
                response.put("totalPages", transactions.getTotalPages());
                response.put("totalElements", transactions.getTotalElements());
                response.put("hasNext", transactions.hasNext());
                response.put("hasPrevious", transactions.hasPrevious());
                response.put("timestamp", LocalDateTime.now().toString());
                response.put("paginationEnabled", true);

                log.info("Returning paginated response: {}/{} elements",
                        transactions.getContent().size(), transactions.getTotalElements());

                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            log.error("❌ Failed to retrieve bank transactions", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve bank transactions: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/bank/{id}")
    public ResponseEntity<Map<String, Object>> getBankTransactionById(@PathVariable Long id) {
        try {
            BankTransactionResponse transaction = transactionService.getBankTransactionById(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Transaction retrieved successfully");
            response.put("data", transaction);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve transaction: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
    }

    @PostMapping("/bank/{transactionId}/match/{studentId}")
    public ResponseEntity<Map<String, Object>> matchBankTransaction(
            @PathVariable Long transactionId,
            @PathVariable Long studentId) {

        try {
            BankTransactionResponse transaction = transactionService.matchBankTransaction(transactionId, studentId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Transaction matched successfully");
            response.put("data", transaction);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to match transaction: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @DeleteMapping("/bank/{id}")
    public ResponseEntity<Map<String, Object>> deleteBankTransaction(@PathVariable Long id) {
        try {
            transactionService.deleteBankTransaction(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Transaction deleted successfully");
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to delete transaction: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    // ========== PAYMENT TRANSACTION ENDPOINTS ==========

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(
            @Valid @RequestBody PaymentVerificationRequest request) {

        try {
            PaymentTransactionResponse transaction = transactionService.verifyPayment(request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment verified successfully");
            response.put("data", transaction);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to verify payment: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @PostMapping("/bulk-verify")
    public ResponseEntity<Map<String, Object>> bulkVerifyPayments(
            @Valid @RequestBody BulkVerificationRequest request) {

        try {
            List<PaymentTransactionResponse> transactions = transactionService.bulkVerifyPayments(request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Successfully verified " + transactions.size() + " payments");
            response.put("data", transactions);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to bulk verify payments: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @GetMapping("/verified")
    public ResponseEntity<Map<String, Object>> getVerifiedTransactions(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "all", defaultValue = "false") Boolean all,
            @PageableDefault(size = 50) Pageable pageable) {

        try {
            if (Boolean.TRUE.equals(all)) {
                // Return all verified transactions
                List<PaymentTransactionResponse> allTransactions = transactionService.getAllVerifiedTransactions(search);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "All verified payments retrieved successfully");
                response.put("data", allTransactions);
                response.put("total", allTransactions.size());
                response.put("timestamp", LocalDateTime.now().toString());
                response.put("paginationEnabled", false);

                return ResponseEntity.ok(response);

            } else {
                // Paginated response
                Page<PaymentTransactionResponse> transactions = transactionService.getVerifiedTransactions(search, pageable);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Verified payments retrieved successfully");
                response.put("data", transactions.getContent());
                response.put("page", transactions.getNumber());
                response.put("size", transactions.getSize());
                response.put("totalPages", transactions.getTotalPages());
                response.put("totalElements", transactions.getTotalElements());
                response.put("timestamp", LocalDateTime.now().toString());
                response.put("paginationEnabled", true);

                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve verified transactions: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/payment/{id}")
    public ResponseEntity<Map<String, Object>> getPaymentTransactionById(@PathVariable Long id) {
        try {
            PaymentTransactionResponse transaction = transactionService.getPaymentTransactionById(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment transaction retrieved successfully");
            response.put("data", transaction);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve payment transaction: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<Map<String, Object>> getStudentTransactions(
            @PathVariable Long studentId) {

        try {
            List<PaymentTransactionResponse> transactions = transactionService.getStudentTransactions(studentId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Student transactions retrieved successfully");
            response.put("data", transactions);
            response.put("count", transactions.size());
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve student transactions: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ========== STATISTICS ENDPOINTS (UPDATED) ==========

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getTransactionStatistics() {
        try {
            TransactionStatisticsResponse statistics = transactionService.getTransactionStatistics();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Statistics retrieved successfully");
            response.put("data", statistics);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve statistics: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/statistics/range")
    public ResponseEntity<Map<String, Object>> getStatisticsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            TransactionStatisticsResponse statistics = transactionService.getStatisticsByDateRange(startDate, endDate);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Statistics retrieved successfully");
            response.put("data", statistics);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve statistics: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @GetMapping("/statistics/summary")
    public ResponseEntity<Map<String, Object>> getTransactionSummary() {
        long startTime = System.currentTimeMillis();
        try {
            log.info("Fetching transaction statistics summary");

            Map<String, Object> summary = new HashMap<>();

            // Get counts using repository methods (optimized)
            long totalTransactions = transactionService.getTotalBankTransactionCount();
            long unverifiedCount = transactionService.getBankTransactionCountByStatus(TransactionStatus.UNVERIFIED);
            long pendingCount = transactionService.getBankTransactionCountByStatus(TransactionStatus.PENDING);
            long matchedCount = transactionService.getBankTransactionCountByStatus(TransactionStatus.MATCHED);
            long verifiedCount = transactionService.getPaymentTransactionCountVerified();
            long cancelledCount = transactionService.getBankTransactionCountByStatus(TransactionStatus.CANCELLED);

            summary.put("totalTransactions", totalTransactions);
            summary.put("unverifiedCount", unverifiedCount);
            summary.put("pendingCount", pendingCount);
            summary.put("matchedCount", matchedCount);
            summary.put("verifiedCount", verifiedCount);
            summary.put("cancelledCount", cancelledCount);

            // Calculate match rate
            double processedCount = matchedCount + verifiedCount;
            String matchRate = totalTransactions > 0 ?
                    String.format("%.1f%%", (processedCount / totalTransactions) * 100) : "0%";
            summary.put("matchRate", matchRate);

            // Get amount totals
            Double totalAmount = transactionService.getTotalVerifiedAmount();
            Double todayAmount = transactionService.getTotalVerifiedAmountToday();

            summary.put("totalAmount", totalAmount != null ? totalAmount : 0.0);
            summary.put("todayAmount", todayAmount != null ? todayAmount : 0.0);

            // Get pending fees from all students
            double totalPendingFees = transactionService.getTotalPendingFees();
            summary.put("totalPendingFees", totalPendingFees);

            // Get recent activity (last 7 days)
            LocalDate weekAgo = LocalDate.now().minusDays(7);
            long recentCount = transactionService.getBankTransactionCountSince(weekAgo);
            summary.put("recentTransactionCount", recentCount);

            // Calculate recent amounts by status
            Map<String, Double> recentAmountsByStatus = transactionService.getRecentAmountsByStatus(weekAgo, LocalDate.now());
            summary.put("recentAmountsByStatus", recentAmountsByStatus);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Transaction summary retrieved successfully");
            response.put("data", summary);
            response.put("timestamp", LocalDateTime.now().toString());
            response.put("processingTime", System.currentTimeMillis() - startTime);

            log.info("✅ Transaction summary generated in {}ms", System.currentTimeMillis() - startTime);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error generating transaction summary", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to generate transaction summary: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ========== SMS ENDPOINTS ==========

    @PostMapping("/sms/send")
    public ResponseEntity<Map<String, Object>> sendPaymentSms(
            @Valid @RequestBody SmsRequest request) {

        try {
            SmsLogResponse smsLog = transactionService.sendPaymentSms(request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "SMS sent successfully");
            response.put("data", smsLog);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to send SMS: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @GetMapping("/sms/logs")
    public ResponseEntity<Map<String, Object>> getSmsLogs(
            @RequestParam(value = "studentId", required = false) Long studentId,
            @RequestParam(value = "paymentTransactionId", required = false) Long paymentTransactionId) {

        try {
            List<SmsLogResponse> logs = transactionService.getSmsLogs(studentId, paymentTransactionId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "SMS logs retrieved successfully");
            response.put("data", logs);
            response.put("count", logs.size());
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve SMS logs: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ========== EXPORT ENDPOINTS ==========

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportTransactionsToCsv(
            @RequestParam(value = "type", defaultValue = "verified") String type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            byte[] csvBytes = transactionService.exportTransactionsToCsv(type, startDate, endDate);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "transactions_" + type + "_" + LocalDate.now() + ".csv");
            headers.setContentLength(csvBytes.length);

            return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/receipt/{paymentTransactionId}/pdf")
    public ResponseEntity<byte[]> generateReceiptPdf(@PathVariable Long paymentTransactionId) {
        try {
            byte[] pdfBytes = transactionService.generateReceiptPdf(paymentTransactionId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "receipt_" + paymentTransactionId + ".pdf");
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Add receipt endpoints for the ReceiptGenerator
    @GetMapping("/{id}/receipt/download")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable Long id) {
        try {
            byte[] pdfBytes = transactionService.generateReceiptPdf(id);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    "receipt-" + id + ".pdf");
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/receipt")
    public ResponseEntity<Map<String, Object>> generateReceipt(@PathVariable Long id) {
        try {
            byte[] pdfBytes = transactionService.generateReceiptPdf(id);

            // Encode PDF to base64 for JSON response
            String base64Receipt = java.util.Base64.getEncoder().encodeToString(pdfBytes);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Receipt generated successfully");
            response.put("transactionId", id);
            response.put("pdfBase64", base64Receipt);
            response.put("fileName", "receipt-" + id + ".pdf");
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to generate receipt: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ========== NEW HELPER ENDPOINTS ==========

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("service", "Transaction Service");
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("version", "1.0.0");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/counts")
    public ResponseEntity<Map<String, Object>> getTransactionCounts() {
        try {
            Map<String, Object> counts = new HashMap<>();

            counts.put("totalBankTransactions", transactionService.getTotalBankTransactionCount());
            counts.put("unverifiedCount", transactionService.getBankTransactionCountByStatus(TransactionStatus.UNVERIFIED));
            counts.put("pendingCount", transactionService.getBankTransactionCountByStatus(TransactionStatus.PENDING));
            counts.put("matchedCount", transactionService.getBankTransactionCountByStatus(TransactionStatus.MATCHED));
            counts.put("verifiedCount", transactionService.getPaymentTransactionCountVerified());
            counts.put("cancelledCount", transactionService.getBankTransactionCountByStatus(TransactionStatus.CANCELLED));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Transaction counts retrieved successfully");
            response.put("data", counts);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve transaction counts: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}