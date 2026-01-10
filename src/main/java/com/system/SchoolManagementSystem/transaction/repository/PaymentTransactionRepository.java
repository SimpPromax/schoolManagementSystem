package com.system.SchoolManagementSystem.transaction.repository;

import com.system.SchoolManagementSystem.transaction.entity.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByReceiptNumber(String receiptNumber);

    List<PaymentTransaction> findByStudentId(Long studentId);

    List<PaymentTransaction> findByIsVerified(Boolean isVerified);

    Page<PaymentTransaction> findByIsVerified(Boolean isVerified, Pageable pageable);

    List<PaymentTransaction> findByPaymentDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    // Add this method for fee calculations
    List<PaymentTransaction> findByStudentIdAndIsVerifiedTrue(Long studentId);

    @Query("SELECT pt FROM PaymentTransaction pt WHERE " +
            "pt.student.id = :studentId AND " +
            "(:isVerified IS NULL OR pt.isVerified = :isVerified)")
    List<PaymentTransaction> findByStudentAndVerificationStatus(
            @Param("studentId") Long studentId,
            @Param("isVerified") Boolean isVerified);

    @Query("SELECT SUM(pt.amount) FROM PaymentTransaction pt WHERE " +
            "pt.isVerified = true AND " +
            "DATE(pt.paymentDate) = CURRENT_DATE")
    Double getTotalVerifiedAmountToday();

    @Query("SELECT SUM(pt.amount) FROM PaymentTransaction pt WHERE pt.isVerified = true")
    Double getTotalVerifiedAmount();

    @Query("SELECT pt FROM PaymentTransaction pt WHERE " +
            "(:search IS NULL OR " +
            "LOWER(pt.receiptNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(pt.student.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(pt.bankReference) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<PaymentTransaction> searchTransactions(@Param("search") String search, Pageable pageable);

    Long countByIsVerified(Boolean isVerified);
}