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

    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.student.id = :studentId")
    Page<PaymentTransaction> findByStudentId(@Param("studentId") Long studentId, Pageable pageable);

    // Removed isVerified condition
    List<PaymentTransaction> findByStudentIdAndIsVerifiedTrue(Long studentId); // ← Still uses IsVerifiedTrue — consider renaming or removing if not needed

    @Query("SELECT pt FROM PaymentTransaction pt WHERE " +
            "pt.student.id = :studentId AND " +
            "(:isVerified IS NULL OR pt.isVerified = :isVerified)")
    List<PaymentTransaction> findByStudentAndVerificationStatus(
            @Param("studentId") Long studentId,
            @Param("isVerified") Boolean isVerified);

    // CHANGED: Return Double instead of BigDecimal — REMOVED isVerified filter
    @Query("SELECT COALESCE(SUM(pt.amount), 0.0) FROM PaymentTransaction pt WHERE " +
            "DATE(pt.paymentDate) = CURRENT_DATE")
    Double getTotalVerifiedAmountToday();

    // CHANGED: Return Double instead of BigDecimal — REMOVED isVerified filter
    @Query("SELECT COALESCE(SUM(pt.amount), 0.0) FROM PaymentTransaction pt")
    Double getTotalVerifiedAmount();

    @Query("SELECT pt FROM PaymentTransaction pt WHERE " +
            "(:search IS NULL OR " +
            "LOWER(pt.receiptNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(pt.student.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(pt.bankReference) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<PaymentTransaction> searchTransactions(@Param("search") String search, Pageable pageable);

    Long countByIsVerified(Boolean isVerified);

    // ========== NEW: Find by bank transaction ==========
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.bankTransaction.id = :bankTransactionId")
    Optional<PaymentTransaction> findByBankTransactionId(@Param("bankTransactionId") Long bankTransactionId);

    // Find all payment transactions with bank transaction details
    @Query("SELECT pt FROM PaymentTransaction pt JOIN FETCH pt.bankTransaction bt WHERE bt IS NOT NULL")
    List<PaymentTransaction> findAllWithBankTransactions();

    // Count payment transactions created from bank transactions
    @Query("SELECT COUNT(pt) FROM PaymentTransaction pt WHERE pt.bankTransaction IS NOT NULL")
    Long countCreatedFromBankTransactions();

    // CHANGED: Simplified payment method distribution (MySQL compatible) — REMOVED isVerified filter
    @Query("SELECT t.paymentMethod, COUNT(t), COALESCE(SUM(t.amount), 0.0) " +
            "FROM PaymentTransaction t " +
            "GROUP BY t.paymentMethod")
    List<Object[]> getPaymentMethodDistribution();

    // CHANGED: SIMPLIFIED - Daily collection trend (MySQL compatible) — REMOVED isVerified filter
    @Query(value = "SELECT " +
            "DATE(payment_date) as period, " +
            "COALESCE(SUM(amount), 0.0) as collected " +
            "FROM payment_transactions " +
            "WHERE payment_date >= :startDate " +
            "AND payment_date <= :endDate " +
            "GROUP BY DATE(payment_date) " +
            "ORDER BY period",
            nativeQuery = true)
    List<Object[]> getCollectionTrend(@Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);

    // NEW: Dynamic period grouping for MySQL — REMOVED isVerified filter
    @Query(value = "SELECT " +
            "CASE :period " +
            "WHEN 'day' THEN DATE(payment_date) " +
            "WHEN 'week' THEN CONCAT(YEAR(payment_date), '-', WEEK(payment_date)) " +
            "WHEN 'month' THEN DATE_FORMAT(payment_date, '%Y-%m') " +
            "WHEN 'quarter' THEN CONCAT(YEAR(payment_date), '-Q', QUARTER(payment_date)) " +
            "WHEN 'year' THEN YEAR(payment_date) " +
            "ELSE DATE(payment_date) " +
            "END as period, " +
            "COALESCE(SUM(amount), 0.0) as collected " +
            "FROM payment_transactions " +
            "WHERE payment_date >= :startDate " +
            "AND payment_date <= :endDate " +
            "GROUP BY " +
            "CASE :period " +
            "WHEN 'day' THEN DATE(payment_date) " +
            "WHEN 'week' THEN CONCAT(YEAR(payment_date), '-', WEEK(payment_date)) " +
            "WHEN 'month' THEN DATE_FORMAT(payment_date, '%Y-%m') " +
            "WHEN 'quarter' THEN CONCAT(YEAR(payment_date), '-Q', QUARTER(payment_date)) " +
            "WHEN 'year' THEN YEAR(payment_date) " +
            "ELSE DATE(payment_date) " +
            "END " +
            "ORDER BY period",
            nativeQuery = true)
    List<Object[]> getCollectionTrendByPeriod(@Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate,
                                              @Param("period") String period);

    // CHANGED: Return Double instead of BigDecimal — REMOVED isVerified filter
    @Query("SELECT COALESCE(SUM(t.amount), 0.0) FROM PaymentTransaction t " +
            "WHERE t.paymentDate >= :sinceDate")
    Double getTotalVerifiedAmountSince(@Param("sinceDate") LocalDateTime sinceDate);

    // NEW: Simple grade-wise collection (MySQL compatible) — REMOVED isVerified filter
    @Query(value = "SELECT " +
            "s.grade, " +
            "COALESCE(SUM(t.amount), 0.0) as collected " +
            "FROM payment_transactions t " +
            "JOIN students s ON s.id = t.student_id " +
            "GROUP BY s.grade",
            nativeQuery = true)
    List<Object[]> getCollectionByGrade();

    // NEW: Alternative grade-wise collection using JPA — REMOVED isVerified filter
    @Query("SELECT t.student.grade, COALESCE(SUM(t.amount), 0.0) " +
            "FROM PaymentTransaction t " +
            "GROUP BY t.student.grade")
    List<Object[]> getCollectionByGradeJpa();

    @Query(
            value = """
        SELECT COUNT(DISTINCT student_id)
        FROM (
            SELECT student_id
            FROM payment_transactions
            GROUP BY student_id
            HAVING COUNT(*) > 1
        ) AS multi_pay_students
        """,
            nativeQuery = true
    )
    Long countStudentsWithMultiplePayments();


    @Query("SELECT MAX(t.paymentDate) FROM PaymentTransaction t " +
            "WHERE t.student.id = :studentId")
    LocalDateTime getLastPaymentDate(@Param("studentId") Long studentId);

    @Query("SELECT COUNT(t) FROM PaymentTransaction t " +
            "WHERE t.student.id = :studentId")
    Long countByStudentId(@Param("studentId") Long studentId);

    // REMOVED isVerified filter
    @Query("SELECT DISTINCT t.paymentMethod FROM PaymentTransaction t " +
            "WHERE t.student.id = :studentId")
    List<String> getPaymentMethodsByStudentId(@Param("studentId") Long studentId);

    // REMOVED isVerified filter
    @Query("SELECT AVG(t.amount) FROM PaymentTransaction t " +
            "WHERE t.student.id = :studentId")
    Double getAveragePaymentByStudentId(@Param("studentId") Long studentId);

    List<PaymentTransaction> findByStudentIdOrderByPaymentDateDesc(Long studentId);

    // Get total amount for a specific date range — REMOVED isVerified filter
    @Query("SELECT COALESCE(SUM(t.amount), 0.0) FROM PaymentTransaction t " +
            "WHERE t.paymentDate >= :startDate " +
            "AND t.paymentDate <= :endDate")
    Double getTotalVerifiedAmountBetween(@Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate);

    // Count transactions for a specific date range — REMOVED isVerified filter
    @Query("SELECT COUNT(t) FROM PaymentTransaction t " +
            "WHERE t.paymentDate >= :startDate " +
            "AND t.paymentDate <= :endDate")
    Long countVerifiedTransactionsBetween(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    // Get average transaction amount for a period — REMOVED isVerified filter
    @Query("SELECT COALESCE(AVG(t.amount), 0.0) FROM PaymentTransaction t " +
            "WHERE t.paymentDate >= :startDate " +
            "AND t.paymentDate <= :endDate")
    Double getAverageTransactionAmountBetween(@Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);

    // Get top 10 highest transactions — REMOVED isVerified filter
    @Query("SELECT t FROM PaymentTransaction t " +
            "ORDER BY t.amount DESC LIMIT 10")
    List<PaymentTransaction> findTop10HighestTransactions();

    // Get payment count by payment method — REMOVED isVerified filter
    @Query("SELECT t.paymentMethod, COUNT(t) FROM PaymentTransaction t " +
            "GROUP BY t.paymentMethod")
    List<Object[]> getPaymentMethodCount();

    // Get transactions by student and date range — REMOVED isVerified filter
    @Query("SELECT t FROM PaymentTransaction t " +
            "WHERE t.student.id = :studentId " +
            "AND t.paymentDate >= :startDate " +
            "AND t.paymentDate <= :endDate " +
            "ORDER BY t.paymentDate DESC")
    List<PaymentTransaction> findVerifiedTransactionsByStudentAndDateRange(
            @Param("studentId") Long studentId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // Get monthly summary — REMOVED isVerified filter
    @Query(value = "SELECT " +
            "DATE_FORMAT(payment_date, '%Y-%m') as month, " +
            "COUNT(*) as transaction_count, " +
            "COALESCE(SUM(amount), 0.0) as total_amount, " +
            "COALESCE(AVG(amount), 0.0) as average_amount " +
            "FROM payment_transactions " +
            "GROUP BY DATE_FORMAT(payment_date, '%Y-%m') " +
            "ORDER BY month DESC",
            nativeQuery = true)
    List<Object[]> getMonthlySummary();

    // Get daily summary for the last 30 days — REMOVED isVerified filter
    @Query(value = "SELECT " +
            "DATE(payment_date) as date, " +
            "COUNT(*) as transaction_count, " +
            "COALESCE(SUM(amount), 0.0) as total_amount " +
            "FROM payment_transactions " +
            "WHERE payment_date >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
            "GROUP BY DATE(payment_date) " +
            "ORDER BY date DESC",
            nativeQuery = true)
    List<Object[]> getDailySummaryLast30Days();

    // Check if receipt number exists
    @Query("SELECT COUNT(t) > 0 FROM PaymentTransaction t WHERE t.receiptNumber = :receiptNumber")
    boolean existsByReceiptNumber(@Param("receiptNumber") String receiptNumber);


}