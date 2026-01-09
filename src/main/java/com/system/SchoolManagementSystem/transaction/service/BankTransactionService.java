package com.system.SchoolManagementSystem.transaction.service;

import com.system.SchoolManagementSystem.transaction.entity.BankTransaction;
import com.system.SchoolManagementSystem.transaction.enums.TransactionStatus;
import com.system.SchoolManagementSystem.transaction.repository.BankTransactionRepository;
import com.system.SchoolManagementSystem.transaction.util.BankStatementParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class BankTransactionService {

    private final BankTransactionRepository bankTransactionRepository;
    private final BankStatementParser bankStatementParser;

    public List<BankTransaction> importFromCsv(MultipartFile file, String bankAccount) {
        try {
            log.info("Importing bank transactions from CSV: {}", file.getOriginalFilename());
            List<BankTransaction> transactions = bankStatementParser.parseCsv(file, bankAccount);
            return bankTransactionRepository.saveAll(transactions);
        } catch (Exception e) {
            log.error("Failed to import CSV transactions", e);
            throw new RuntimeException("Failed to import CSV: " + e.getMessage());
        }
    }

    public List<BankTransaction> importFromExcel(MultipartFile file, String bankAccount) {
        try {
            log.info("Importing bank transactions from Excel: {}", file.getOriginalFilename());
            List<BankTransaction> transactions = bankStatementParser.parseExcel(file, bankAccount);
            return bankTransactionRepository.saveAll(transactions);
        } catch (Exception e) {
            log.error("Failed to import Excel transactions", e);
            throw new RuntimeException("Failed to import Excel: " + e.getMessage());
        }
    }

    public List<BankTransaction> importFromPdf(MultipartFile file, String bankAccount) {
        // PDF parsing not implemented yet
        log.warn("PDF import not implemented yet");
        return new ArrayList<>();
    }

    public BankTransaction getById(Long id) {
        return bankTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bank transaction not found with id: " + id));
    }

    public Page<BankTransaction> getAllTransactions(Pageable pageable) {
        return bankTransactionRepository.findAll(pageable);
    }

    public Page<BankTransaction> getByStatus(TransactionStatus status, Pageable pageable) {
        return bankTransactionRepository.findByStatus(status, pageable);
    }

    public List<BankTransaction> getByDateRange(LocalDate startDate, LocalDate endDate) {
        return bankTransactionRepository.findByTransactionDateBetween(startDate, endDate);
    }

    public BankTransaction updateStatus(Long id, TransactionStatus status) {
        BankTransaction transaction = getById(id);
        transaction.setStatus(status);
        return bankTransactionRepository.save(transaction);
    }

    public BankTransaction matchToStudent(Long transactionId, Long studentId) {
        // This method would need student repository injection
        // For now, just update status to MATCHED
        BankTransaction transaction = getById(transactionId);
        transaction.setStatus(TransactionStatus.MATCHED);
        transaction.setMatchedAt(java.time.LocalDateTime.now());
        return bankTransactionRepository.save(transaction);
    }

    public void deleteTransaction(Long id) {
        if (!bankTransactionRepository.existsById(id)) {
            throw new RuntimeException("Transaction not found with id: " + id);
        }
        bankTransactionRepository.deleteById(id);
        log.info("Deleted bank transaction with id: {}", id);
    }

    public void deleteByImportBatch(String importBatchId) {
        List<BankTransaction> transactions = bankTransactionRepository.findByImportBatchId(importBatchId);
        bankTransactionRepository.deleteAll(transactions);
        log.info("Deleted {} transactions from import batch: {}", transactions.size(), importBatchId);
    }

    public Long countByStatus(TransactionStatus status) {
        Long count = bankTransactionRepository.countByStatus(status);
        return count != null ? count : 0L;
    }

    public Double sumAmountByStatusAndDate(TransactionStatus status, LocalDate startDate) {
        Double sum = bankTransactionRepository.sumAmountByStatusAndDate(status, startDate);
        return sum != null ? sum : 0.0;
    }

    public List<BankTransaction> findUnmatchedTransactions() {
        return bankTransactionRepository.findByStatus(TransactionStatus.UNVERIFIED);
    }

    public List<BankTransaction> autoMatchTransactions() {
        log.info("Auto-matching transactions (placeholder implementation)");
        // This would implement matching logic
        // For now, return empty list
        return new ArrayList<>();
    }
}