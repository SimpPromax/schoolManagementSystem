package com.system.SchoolManagementSystem.transaction.service;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.transaction.dto.response.FeeAssignmentResponse;
import com.system.SchoolManagementSystem.transaction.entity.FeeInstallment;
import com.system.SchoolManagementSystem.transaction.entity.FeeStructure;
import com.system.SchoolManagementSystem.transaction.entity.StudentFeeAssignment;
import com.system.SchoolManagementSystem.transaction.enums.FeeStatus;
import com.system.SchoolManagementSystem.transaction.repository.FeeInstallmentRepository;
import com.system.SchoolManagementSystem.transaction.repository.FeeStructureRepository;
import com.system.SchoolManagementSystem.transaction.repository.StudentFeeAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class FeeAssignmentService {

    private final StudentFeeAssignmentRepository feeAssignmentRepository;
    private final StudentRepository studentRepository;
    private final FeeStructureRepository feeStructureRepository;
    private final FeeInstallmentRepository feeInstallmentRepository;

    public StudentFeeAssignment assignFeeToStudent(Long studentId, Long feeStructureId, String academicYear) {
        log.info("Assigning fee structure {} to student {} for academic year {}",
                feeStructureId, studentId, academicYear);

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + studentId));

        FeeStructure feeStructure = feeStructureRepository.findById(feeStructureId)
                .orElseThrow(() -> new RuntimeException("Fee structure not found with id: " + feeStructureId));

        // Check if assignment already exists
        feeAssignmentRepository.findByStudentIdAndFeeStructureIdAndAcademicYear(studentId, feeStructureId, academicYear)
                .ifPresent(existing -> {
                    throw new RuntimeException("Fee already assigned to this student for academic year " + academicYear);
                });

        StudentFeeAssignment assignment = StudentFeeAssignment.builder()
                .student(student)
                .feeStructure(feeStructure)
                .academicYear(academicYear)
                .assignedDate(LocalDate.now())
                .totalAmount(feeStructure.getTotalAmount())
                .paidAmount(0.0)
                .pendingAmount(feeStructure.getTotalAmount())
                .feeStatus(FeeStatus.PENDING)
                .isActive(true)
                .build();

        // Create installments if needed
        if (feeStructure.getInstallmentCount() > 1) {
            List<FeeInstallment> installments = createInstallments(assignment, feeStructure);
            assignment.setInstallments(installments);
        }

        return feeAssignmentRepository.save(assignment);
    }

    public StudentFeeAssignment updateFeeAssignment(Long assignmentId, Double paidAmount, FeeStatus status) {
        StudentFeeAssignment assignment = getById(assignmentId);

        if (paidAmount != null) {
            assignment.setPaidAmount(paidAmount);
        }

        if (status != null) {
            assignment.setFeeStatus(status);
        }

        // Update last payment date if payment made
        if (paidAmount != null && paidAmount > 0) {
            assignment.setLastPaymentDate(LocalDate.now());
        }

        return feeAssignmentRepository.save(assignment);
    }

    public StudentFeeAssignment updatePayment(Long assignmentId, Double paymentAmount) {
        StudentFeeAssignment assignment = getById(assignmentId);

        double newPaidAmount = assignment.getPaidAmount() + paymentAmount;
        assignment.setPaidAmount(newPaidAmount);
        assignment.setLastPaymentDate(LocalDate.now());

        return feeAssignmentRepository.save(assignment);
    }

    public StudentFeeAssignment getById(Long id) {
        return feeAssignmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fee assignment not found with id: " + id));
    }

    public List<StudentFeeAssignment> getByStudentId(Long studentId) {
        return feeAssignmentRepository.findByStudentId(studentId);
    }

    public List<StudentFeeAssignment> getActiveByStudentId(Long studentId) {
        return feeAssignmentRepository.findByStudentIdAndIsActive(studentId, true);
    }

    public List<StudentFeeAssignment> getByFeeStatus(FeeStatus status) {
        return feeAssignmentRepository.findByFeeStatus(status);
    }

    public List<StudentFeeAssignment> getOverdueAssignments() {
        List<StudentFeeAssignment> allAssignments = feeAssignmentRepository.findAll();
        return allAssignments.stream()
                .filter(assignment -> assignment.getDueDate() != null)
                .filter(assignment -> LocalDate.now().isAfter(assignment.getDueDate()))
                .filter(assignment -> assignment.getFeeStatus() != FeeStatus.PAID)
                .collect(Collectors.toList());
    }

    public FeeAssignmentResponse getFeeAssignmentDetails(Long id) {
        StudentFeeAssignment assignment = getById(id);

        FeeAssignmentResponse response = new FeeAssignmentResponse();
        response.setId(assignment.getId());
        response.setStudentId(assignment.getStudent().getId());
        response.setStudentName(assignment.getStudent().getFullName());
        response.setStudentGrade(assignment.getStudent().getGrade());
        response.setFeeStructureId(assignment.getFeeStructure().getId());
        response.setFeeStructureName(assignment.getFeeStructure().getStructureName());
        response.setAcademicYear(assignment.getAcademicYear());
        response.setAssignedDate(assignment.getAssignedDate());
        response.setTotalAmount(assignment.getTotalAmount());
        response.setPaidAmount(assignment.getPaidAmount());
        response.setPendingAmount(assignment.getPendingAmount());
        response.setFeeStatus(assignment.getFeeStatus());
        response.setLastPaymentDate(assignment.getLastPaymentDate());
        response.setDueDate(assignment.getDueDate());
        response.setRemindersSent(assignment.getRemindersSent());
        response.setLastReminderDate(assignment.getLastReminderDate());
        response.setIsActive(assignment.getIsActive());
        response.setCreatedAt(assignment.getCreatedAt());

        // Convert installments
        if (assignment.getInstallments() != null && !assignment.getInstallments().isEmpty()) {
            List<FeeAssignmentResponse.InstallmentResponse> installmentResponses =
                    assignment.getInstallments().stream()
                            .map(this::convertToInstallmentResponse)
                            .collect(Collectors.toList());
            response.setInstallments(installmentResponses);
        }

        return response;
    }

    public void deactivateAssignment(Long assignmentId) {
        StudentFeeAssignment assignment = getById(assignmentId);
        assignment.setIsActive(false);
        feeAssignmentRepository.save(assignment);
        log.info("Deactivated fee assignment with id: {}", assignmentId);
    }

    public void recalculatePendingAmount(Long assignmentId) {
        StudentFeeAssignment assignment = getById(assignmentId);
        assignment.setPendingAmount(assignment.getTotalAmount() - assignment.getPaidAmount());
        feeAssignmentRepository.save(assignment);
    }

    public Double getTotalPendingAmount() {
        List<StudentFeeAssignment> assignments = feeAssignmentRepository.findAll();
        return assignments.stream()
                .mapToDouble(StudentFeeAssignment::getPendingAmount)
                .sum();
    }

    public Long countByStatus(FeeStatus status) {
        return (long) getByFeeStatus(status).size();
    }

    public void sendFeeReminders() {
        log.info("Sending fee reminders (placeholder implementation)");
        // Implement reminder logic here
    }

    public void sendOverdueNotifications() {
        log.info("Sending overdue notifications (placeholder implementation)");
        // Implement overdue notification logic here
    }

    // ========== Helper Methods ==========

    private List<FeeInstallment> createInstallments(StudentFeeAssignment assignment, FeeStructure feeStructure) {
        List<FeeInstallment> installments = new ArrayList<>();
        double installmentAmount = feeStructure.getTotalAmount() / feeStructure.getInstallmentCount();

        for (int i = 1; i <= feeStructure.getInstallmentCount(); i++) {
            LocalDate dueDate = LocalDate.now().plusMonths(i - 1); // First installment due now, others monthly

            FeeInstallment installment = FeeInstallment.builder()
                    .feeAssignment(assignment)
                    .installmentNumber(i)
                    .installmentName("Installment " + i)
                    .amount(installmentAmount)
                    .paidAmount(0.0)
                    .dueDate(dueDate)
                    .status(FeeStatus.PENDING)
                    .lateFeeCharged(0.0)
                    .discountAmount(0.0)
                    .netAmount(installmentAmount)
                    .paymentDeadline(dueDate.plusDays(7))
                    .build();

            installments.add(installment);
        }

        return feeInstallmentRepository.saveAll(installments);
    }

    private FeeAssignmentResponse.InstallmentResponse convertToInstallmentResponse(FeeInstallment installment) {
        FeeAssignmentResponse.InstallmentResponse response = new FeeAssignmentResponse.InstallmentResponse();
        response.setId(installment.getId());
        response.setInstallmentNumber(installment.getInstallmentNumber());
        response.setInstallmentName(installment.getInstallmentName());
        response.setAmount(installment.getAmount());
        response.setPaidAmount(installment.getPaidAmount());
        response.setDueDate(installment.getDueDate());
        response.setStatus(installment.getStatus());
        response.setLateFeeCharged(installment.getLateFeeCharged());
        response.setDiscountAmount(installment.getDiscountAmount());
        response.setNetAmount(installment.getNetAmount());
        response.setPaymentDeadline(installment.getPaymentDeadline());
        return response;
    }
}