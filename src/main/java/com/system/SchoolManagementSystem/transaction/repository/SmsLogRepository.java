package com.system.SchoolManagementSystem.transaction.repository;

import com.system.SchoolManagementSystem.transaction.entity.SmsLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SmsLogRepository extends JpaRepository<SmsLog, Long> {

    List<SmsLog> findByStudentId(Long studentId);

    List<SmsLog> findByPaymentTransactionId(Long paymentTransactionId);

    List<SmsLog> findByStatus(SmsLog.SmsStatus status);

    List<SmsLog> findBySentAtBetween(LocalDateTime startDate, LocalDateTime endDate);
}