package com.system.SchoolManagementSystem.transaction.controller;

import com.system.SchoolManagementSystem.transaction.dto.request.*;
import com.system.SchoolManagementSystem.transaction.dto.response.*;
import com.system.SchoolManagementSystem.transaction.enums.TransactionStatus;
import com.system.SchoolManagementSystem.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class TransactionController {

    private final TransactionService transactionService;

    // Bank Transaction Endpoints

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
            @PageableDefault(size = 20) Pageable pageable) {

        try {
            Page<BankTransactionResponse> transactions = transactionService.getBankTransactions(status, search, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Bank transactions retrieved successfully");
            response.put("data", transactions.getContent());
            response.put("page", transactions.getNumber());
            response.put("size", transactions.getSize());
            response.put("totalPages", transactions.getTotalPages());
            response.put("totalElements", transactions.getTotalElements());
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
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

    // Payment Transaction Endpoints

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
            @PageableDefault(size = 20) Pageable pageable) {

        try {
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

            return ResponseEntity.ok(response);

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

    // Statistics Endpoints

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

    // SMS Endpoints

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

    // Export Endpoints

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
}