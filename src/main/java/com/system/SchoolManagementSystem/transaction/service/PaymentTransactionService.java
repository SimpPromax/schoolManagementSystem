package com.system.SchoolManagementSystem.transaction.service;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.transaction.dto.request.PaymentVerificationRequest;
import com.system.SchoolManagementSystem.transaction.entity.BankTransaction;
import com.system.SchoolManagementSystem.transaction.entity.PaymentTransaction;
import com.system.SchoolManagementSystem.transaction.entity.StudentFeeAssignment;
import com.system.SchoolManagementSystem.transaction.repository.BankTransactionRepository;
import com.system.SchoolManagementSystem.transaction.repository.PaymentTransactionRepository;
import com.system.SchoolManagementSystem.transaction.repository.StudentFeeAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PaymentTransactionService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final StudentRepository studentRepository;
    private final StudentFeeAssignmentRepository feeAssignmentRepository;

    public PaymentTransaction createPayment(PaymentVerificationRequest request) {
        log.info("Creating payment for student: {}", request.getStudentId());

        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + request.getStudentId()));

        // Find bank transaction if provided
        BankTransaction bankTransaction = null;
        if (request.getBankTransactionId() != null) {
            bankTransaction = bankTransactionRepository.findById(request.getBankTransactionId())
                    .orElseThrow(() -> new RuntimeException("Bank transaction not found with id: " + request.getBankTransactionId()));
        }

        // Find fee assignment if provided
        StudentFeeAssignment feeAssignment = null;
        if (request.getFeeAssignmentId() != null) {
            feeAssignment = feeAssignmentRepository.findById(request.getFeeAssignmentId())
                    .orElseThrow(() -> new RuntimeException("Fee assignment not found with id: " + request.getFeeAssignmentId()));
        }

        PaymentTransaction payment = PaymentTransaction.builder()
                .student(student)
                .feeAssignment(feeAssignment)
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .paymentDate(request.getPaymentDate() != null ?
                        request.getPaymentDate().atStartOfDay() : LocalDateTime.now())
                .bankTransaction(bankTransaction)
                .bankReference(bankTransaction != null ? bankTransaction.getBankReference() : null)
                .isVerified(false)
                .paymentFor(request.getPaymentFor())
                .discountApplied(request.getDiscountApplied())
                .lateFeePaid(request.getLateFeePaid())
                .convenienceFee(request.getConvenienceFee())
                .notes(request.getNotes())
                .build();

        return paymentTransactionRepository.save(payment);
    }

    public PaymentTransaction verifyPayment(Long paymentId, Long verifiedByUserId) {
        PaymentTransaction payment = getById(paymentId);

        payment.setIsVerified(true);
        payment.setVerifiedAt(LocalDateTime.now());
        // In real implementation, you would fetch the User entity
        // payment.setVerifiedBy(userRepository.findById(verifiedByUserId).orElse(null));

        return paymentTransactionRepository.save(payment);
    }

    public PaymentTransaction updatePayment(Long paymentId, PaymentVerificationRequest request) {
        PaymentTransaction payment = getById(paymentId);

        if (request.getAmount() != null) {
            payment.setAmount(request.getAmount());
        }

        if (request.getPaymentMethod() != null) {
            payment.setPaymentMethod(request.getPaymentMethod());
        }

        if (request.getPaymentDate() != null) {
            payment.setPaymentDate(request.getPaymentDate().atStartOfDay());
        }

        if (request.getNotes() != null) {
            payment.setNotes(request.getNotes());
        }

        payment.setPaymentFor(request.getPaymentFor());
        payment.setDiscountApplied(request.getDiscountApplied());
        payment.setLateFeePaid(request.getLateFeePaid());
        payment.setConvenienceFee(request.getConvenienceFee());

        return paymentTransactionRepository.save(payment);
    }

    public PaymentTransaction getById(Long id) {
        return paymentTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment transaction not found with id: " + id));
    }

    public PaymentTransaction getByReceiptNumber(String receiptNumber) {
        return paymentTransactionRepository.findByReceiptNumber(receiptNumber)
                .orElseThrow(() -> new RuntimeException("Payment transaction not found with receipt number: " + receiptNumber));
    }

    public Page<PaymentTransaction> getAllPayments(Pageable pageable) {
        return paymentTransactionRepository.findAll(pageable);
    }

    public Page<PaymentTransaction> getVerifiedPayments(Pageable pageable) {
        return paymentTransactionRepository.findByIsVerified(true, pageable);
    }

    public List<PaymentTransaction> getByStudentId(Long studentId) {
        return paymentTransactionRepository.findByStudentId(studentId);
    }

    public List<PaymentTransaction> getByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return paymentTransactionRepository.findByPaymentDateBetween(startDate, endDate);
    }

    public Double getTotalVerifiedAmount() {
        Double total = paymentTransactionRepository.getTotalVerifiedAmount();
        return total != null ? total : 0.0;
    }

    public Double getTotalVerifiedAmountToday() {
        Double todayTotal = paymentTransactionRepository.getTotalVerifiedAmountToday();
        return todayTotal != null ? todayTotal : 0.0;
    }

    public Double getTotalAmountByStudent(Long studentId) {
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByStudentIdAndIsVerifiedTrue(studentId);
        return transactions.stream()
                .mapToDouble(PaymentTransaction::getAmount)
                .sum();
    }

    public void deletePayment(Long id) {
        if (!paymentTransactionRepository.existsById(id)) {
            throw new RuntimeException("Payment transaction not found with id: " + id);
        }
        paymentTransactionRepository.deleteById(id);
        log.info("Deleted payment transaction with id: {}", id);
    }

    public PaymentTransaction linkToBankTransaction(Long paymentId, Long bankTransactionId) {
        PaymentTransaction payment = getById(paymentId);
        BankTransaction bankTransaction = bankTransactionRepository.findById(bankTransactionId)
                .orElseThrow(() -> new RuntimeException("Bank transaction not found with id: " + bankTransactionId));

        payment.setBankTransaction(bankTransaction);
        payment.setBankReference(bankTransaction.getBankReference());

        return paymentTransactionRepository.save(payment);
    }

    public PaymentTransaction unlinkBankTransaction(Long paymentId) {
        PaymentTransaction payment = getById(paymentId);

        payment.setBankTransaction(null);
        payment.setBankReference(null);

        return paymentTransactionRepository.save(payment);
    }
}