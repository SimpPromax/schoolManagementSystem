package com.system.SchoolManagementSystem.transaction.service;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.transaction.entity.PaymentTransaction;
import com.system.SchoolManagementSystem.transaction.entity.SmsLog;
import com.system.SchoolManagementSystem.transaction.repository.PaymentTransactionRepository;
import com.system.SchoolManagementSystem.transaction.repository.SmsLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SmsService {

    private final SmsLogRepository smsLogRepository;
    private final StudentRepository studentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    public SmsLog sendPaymentConfirmationSms(Long studentId, Long paymentTransactionId, String customMessage) {
        log.info("Sending payment confirmation SMS for transaction: {}", paymentTransactionId);

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + studentId));

        PaymentTransaction paymentTransaction = paymentTransactionRepository.findById(paymentTransactionId)
                .orElseThrow(() -> new RuntimeException("Payment transaction not found with id: " + paymentTransactionId));

        // Get phone number
        String phoneNumber = getStudentPhoneNumber(student);
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new RuntimeException("No phone number available for student: " + student.getFullName());
        }

        // Create message
        String message = customMessage != null ? customMessage :
                String.format("Dear Parent, payment of ₹%.2f has been received for %s. Receipt No: %s. Thank you!",
                        paymentTransaction.getAmount(),
                        paymentTransaction.getPaymentFor() != null ? paymentTransaction.getPaymentFor() : "school fees",
                        paymentTransaction.getReceiptNumber());

        // Create SMS log entity (not logger)
        SmsLog smsLogEntity = SmsLog.builder()
                .student(student)
                .paymentTransaction(paymentTransaction)
                .recipientPhone(phoneNumber)
                .message(message)
                .status(SmsLog.SmsStatus.SENT)
                .gatewayMessageId("SIM-" + UUID.randomUUID().toString().substring(0, 8))
                .sentAt(LocalDateTime.now())
                .build();

        // In real implementation, call SMS gateway API here
        simulateSmsSending(phoneNumber, message, smsLogEntity);

        SmsLog savedLog = smsLogRepository.save(smsLogEntity);

        // Update payment transaction
        paymentTransaction.setSmsSent(true);
        paymentTransaction.setSmsSentAt(LocalDateTime.now());
        paymentTransaction.setSmsId(savedLog.getGatewayMessageId());
        paymentTransactionRepository.save(paymentTransaction);

        return savedLog;
    }

    public SmsLog sendFeeReminderSms(Long studentId, Double pendingAmount, LocalDate dueDate) {
        log.info("Sending fee reminder SMS for student: {}", studentId);

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + studentId));

        String phoneNumber = getStudentPhoneNumber(student);
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new RuntimeException("No phone number available for student: " + student.getFullName());
        }

        String message = String.format("Dear Parent, fee reminder for %s. Pending amount: ₹%.2f. Due date: %s. Please pay at earliest.",
                student.getFullName(),
                pendingAmount,
                dueDate != null ? dueDate.toString() : "ASAP");

        SmsLog smsLogEntity = SmsLog.builder()
                .student(student)
                .recipientPhone(phoneNumber)
                .message(message)
                .status(SmsLog.SmsStatus.SENT)
                .gatewayMessageId("REM-" + UUID.randomUUID().toString().substring(0, 8))
                .sentAt(LocalDateTime.now())
                .build();

        simulateSmsSending(phoneNumber, message, smsLogEntity);

        return smsLogRepository.save(smsLogEntity);
    }

    public SmsLog sendGeneralSms(Long studentId, String message) {
        log.info("Sending general SMS for student: {}", studentId);

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + studentId));

        String phoneNumber = getStudentPhoneNumber(student);
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new RuntimeException("No phone number available for student: " + student.getFullName());
        }

        SmsLog smsLogEntity = SmsLog.builder()
                .student(student)
                .recipientPhone(phoneNumber)
                .message(message)
                .status(SmsLog.SmsStatus.SENT)
                .gatewayMessageId("GEN-" + UUID.randomUUID().toString().substring(0, 8))
                .sentAt(LocalDateTime.now())
                .build();

        simulateSmsSending(phoneNumber, message, smsLogEntity);

        return smsLogRepository.save(smsLogEntity);
    }

    public SmsLog getSmsLogById(Long id) {
        return smsLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("SMS log not found with id: " + id));
    }

    public SmsLog updateSmsStatus(Long smsId, String status, String gatewayMessageId) {
        SmsLog smsLogEntity = getSmsLogById(smsId);

        try {
            smsLogEntity.setStatus(SmsLog.SmsStatus.valueOf(status.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid SMS status: " + status);
        }

        if (gatewayMessageId != null) {
            smsLogEntity.setGatewayMessageId(gatewayMessageId);
        }

        // Update delivery timestamp if status is DELIVERED
        if ("DELIVERED".equalsIgnoreCase(status)) {
            smsLogEntity.setDeliveredAt(LocalDateTime.now());
            smsLogEntity.setDeliveryStatus("Delivered");
        }

        return smsLogRepository.save(smsLogEntity);
    }

    public boolean checkSmsBalance() {
        // In real implementation, check with SMS gateway
        log.info("Checking SMS balance (simulated)");
        return true; // Simulated success
    }

    public Double getSmsBalance() {
        // In real implementation, get balance from SMS gateway
        log.info("Getting SMS balance (simulated)");
        return 100.0; // Simulated balance
    }

    public String getSmsTemplate(String templateName) {
        // Simple template system
        switch (templateName.toUpperCase()) {
            case "PAYMENT_CONFIRMATION":
                return "Dear Parent, payment of ₹{amount} has been received for {purpose}. Receipt No: {receipt}. Thank you!";
            case "FEE_REMINDER":
                return "Dear Parent, fee reminder for {student}. Pending amount: ₹{amount}. Due date: {dueDate}. Please pay at earliest.";
            case "OVERDUE_NOTICE":
                return "Urgent: Fee overdue for {student}. Amount: ₹{amount}. Please pay immediately to avoid penalties.";
            default:
                return "Dear Parent, {message}";
        }
    }

    public void updateDeliveryStatus(String gatewayMessageId, String status) {
        smsLogRepository.findAll().stream()
                .filter(logEntry -> gatewayMessageId.equals(logEntry.getGatewayMessageId()))
                .findFirst()
                .ifPresent(logEntry -> {
                    try {
                        logEntry.setStatus(SmsLog.SmsStatus.valueOf(status.toUpperCase()));
                        if ("DELIVERED".equalsIgnoreCase(status)) {
                            logEntry.setDeliveredAt(LocalDateTime.now());
                            logEntry.setDeliveryStatus("Delivered");
                        }
                        smsLogRepository.save(logEntry);
                        log.info("Updated delivery status for SMS {}: {}", gatewayMessageId, status);
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid status for SMS delivery update: {}", status);
                    }
                });
    }

    // ========== Helper Methods ==========

    private String getStudentPhoneNumber(Student student) {
        // Try student phone first
        if (student.getPhone() != null && !student.getPhone().trim().isEmpty()) {
            return cleanPhoneNumber(student.getPhone());
        }

        // Try emergency contact phone
        if (student.getEmergencyContactPhone() != null && !student.getEmergencyContactPhone().trim().isEmpty()) {
            return cleanPhoneNumber(student.getEmergencyContactPhone());
        }

        return null;
    }

    private String cleanPhoneNumber(String phone) {
        if (phone == null) return null;

        // Remove all non-digit characters
        String cleaned = phone.replaceAll("[^\\d]", "");

        // Format as Indian number if 10 digits
        if (cleaned.length() == 10) {
            return "+91" + cleaned;
        }

        return phone;
    }

    private void simulateSmsSending(String phoneNumber, String message, SmsLog smsLogEntity) {
        // Simulate SMS sending (replace with actual SMS gateway API call)
        log.info("SIMULATED SMS: To: {}, Message: {}", phoneNumber, message);

        // Simulate random delivery status updates
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Simulate network delay

                // Randomly decide if SMS was delivered or failed
                if (Math.random() > 0.1) { // 90% success rate
                    smsLogEntity.setStatus(SmsLog.SmsStatus.DELIVERED);
                    smsLogEntity.setDeliveredAt(LocalDateTime.now());
                    smsLogEntity.setDeliveryStatus("Delivered to handset");
                } else {
                    smsLogEntity.setStatus(SmsLog.SmsStatus.FAILED);
                    smsLogEntity.setDeliveryStatus("Failed to deliver");
                }

                smsLogRepository.save(smsLogEntity);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}