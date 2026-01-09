package com.system.SchoolManagementSystem.transaction.repository;

import com.system.SchoolManagementSystem.transaction.entity.BankTransaction;
import com.system.SchoolManagementSystem.transaction.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    Optional<BankTransaction> findByBankReference(String bankReference);

    List<BankTransaction> findByStatus(TransactionStatus status);

    Page<BankTransaction> findByStatus(TransactionStatus status, Pageable pageable);

    List<BankTransaction> findByStudentId(Long studentId);

    List<BankTransaction> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("SELECT COUNT(bt) FROM BankTransaction bt WHERE bt.status = :status")
    Long countByStatus(@Param("status") TransactionStatus status);

    @Query("SELECT SUM(bt.amount) FROM BankTransaction bt WHERE bt.status = :status AND bt.transactionDate >= :startDate")
    Double sumAmountByStatusAndDate(@Param("status") TransactionStatus status, @Param("startDate") LocalDate startDate);

    List<BankTransaction> findByImportBatchId(String importBatchId);

    @Query("SELECT bt FROM BankTransaction bt WHERE " +
            "(:search IS NULL OR " +
            "LOWER(bt.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "bt.bankReference LIKE CONCAT('%', :search, '%') OR " +
            "(bt.student IS NOT NULL AND LOWER(bt.student.fullName) LIKE LOWER(CONCAT('%', :search, '%'))))")
    Page<BankTransaction> searchTransactions(@Param("search") String search, Pageable pageable);
}