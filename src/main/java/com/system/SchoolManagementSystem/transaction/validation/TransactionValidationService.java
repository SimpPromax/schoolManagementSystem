package com.system.SchoolManagementSystem.transaction.validation;

import com.system.SchoolManagementSystem.termmanagement.entity.TermFeeItem;
import com.system.SchoolManagementSystem.termmanagement.repository.StudentTermAssignmentRepository;
import com.system.SchoolManagementSystem.termmanagement.repository.TermFeeItemRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionValidationService {

    private final StudentTermAssignmentRepository studentTermAssignmentRepository;
    private final TermFeeItemRepository termFeeItemRepository;

    /**
     * Validate student has term assignments and unpaid fee items
     */
    public ValidationResult validateStudentForPayment(Long studentId, String studentName) {
        ValidationResult result = new ValidationResult();
        result.setStudentId(studentId);
        result.setStudentName(studentName);

        try {
            // 1. Check if student has ANY term assignments
            boolean hasTermAssignments = studentTermAssignmentRepository.hasTermAssignments(studentId);
            result.setHasTermAssignments(hasTermAssignments);

            if (!hasTermAssignments) {
                result.setValid(false);
                result.setMessage("No term assignments found for student");
                result.setErrorCode("NO_TERM_ASSIGNMENTS");
                return result;
            }

            // 2. Get term assignment count
            Integer assignmentCount = studentTermAssignmentRepository.countTermAssignments(studentId);
            result.setTermAssignmentCount(assignmentCount != null ? assignmentCount : 0);

            // 3. Get all fee items for the student in a single query
            List<TermFeeItem> allFeeItems = termFeeItemRepository.findByStudentTermAssignmentStudentId(studentId);
            result.setFeeItemCount((long) allFeeItems.size());

            if (allFeeItems.isEmpty()) {
                result.setValid(false);
                result.setMessage("No fee items found in any term assignments");
                result.setErrorCode("NO_FEE_ITEMS");
                return result;
            }

            // 4. Get unpaid items in a single query
            List<TermFeeItem> unpaidItems = termFeeItemRepository.findUnpaidItemsByStudentOrdered(studentId);
            result.setUnpaidFeeItemCount((long) unpaidItems.size());

            if (unpaidItems.isEmpty()) {
                result.setValid(false);
                result.setMessage("No unpaid fee items found (all fees may already be paid)");
                result.setErrorCode("NO_UNPAID_ITEMS");
                return result;
            }

            // 5. Calculate total pending amount from unpaid items
            double totalPending = unpaidItems.stream()
                    .mapToDouble(TermFeeItem::getPendingAmount)
                    .sum();

            result.setTotalPendingAmount(totalPending);
            result.setValid(true);
            result.setMessage("Student is eligible for payment");
            result.setErrorCode("ELIGIBLE");

        } catch (Exception e) {
            log.error("Error validating student {}: {}", studentId, e.getMessage(), e);
            result.setValid(false);
            result.setMessage("Validation error: " + e.getMessage());
            result.setErrorCode("VALIDATION_ERROR");
        }

        return result;
    }

    /**
     * Batch validation for multiple students
     */
    public Map<Long, ValidationResult> batchValidateStudents(Set<Long> studentIds) {
        Map<Long, ValidationResult> results = new HashMap<>();

        if (studentIds == null || studentIds.isEmpty()) {
            return results;
        }

        // Get batch data in single queries
        Set<Long> studentsWithAssignments = new HashSet<>(
                studentTermAssignmentRepository.findStudentsWithTermAssignments(studentIds)
        );

        Map<Long, Integer> assignmentCounts = studentTermAssignmentRepository
                .batchCountTermAssignments(studentIds)
                .stream()
                .collect(Collectors.toMap(
                        arr -> ((Number) arr[0]).longValue(),
                        arr -> ((Number) arr[1]).intValue()
                ));

        Map<Long, Object[]> pendingInfo = studentTermAssignmentRepository
                .batchGetPendingTermAssignmentInfo(studentIds)
                .stream()
                .collect(Collectors.toMap(
                        arr -> ((Number) arr[0]).longValue(),
                        arr -> arr
                ));

        // Create results
        for (Long studentId : studentIds) {
            ValidationResult result = new ValidationResult();
            result.setStudentId(studentId);

            boolean hasAssignments = studentsWithAssignments.contains(studentId);
            result.setHasTermAssignments(hasAssignments);

            if (!hasAssignments) {
                result.setValid(false);
                result.setMessage("No term assignments found");
                result.setErrorCode("NO_TERM_ASSIGNMENTS");
                results.put(studentId, result);
                continue;
            }

            result.setTermAssignmentCount(assignmentCounts.getOrDefault(studentId, 0));

            Object[] pendingData = pendingInfo.get(studentId);
            if (pendingData != null) {
                boolean hasPending = (Boolean) pendingData[1];
                int pendingCount = ((Number) pendingData[2]).intValue();
                double pendingAmount = ((Number) pendingData[3]).doubleValue();

                result.setHasPendingAssignments(hasPending);
                result.setPendingAssignmentCount(pendingCount);
                result.setTotalPendingAmount(pendingAmount);

                if (!hasPending || pendingAmount <= 0) {
                    result.setValid(false);
                    result.setMessage("No pending fee amount");
                    result.setErrorCode("NO_PENDING_AMOUNT");
                } else {
                    result.setValid(true);
                    result.setMessage("Eligible for payment");
                    result.setErrorCode("ELIGIBLE");
                }
            } else {
                result.setValid(false);
                result.setMessage("No pending assignment data found");
                result.setErrorCode("NO_PENDING_DATA");
            }

            results.put(studentId, result);
        }

        return results;
    }

    /**
     * Validate specific fee items for payment
     */
    public ValidationResult validateFeeItemsForPayment(Long studentId, Set<Long> feeItemIds) {
        ValidationResult result = new ValidationResult();
        result.setStudentId(studentId);

        try {
            // Check if student exists and has term assignments
            boolean hasTermAssignments = studentTermAssignmentRepository.hasTermAssignments(studentId);
            if (!hasTermAssignments) {
                result.setValid(false);
                result.setMessage("Student has no term assignments");
                result.setErrorCode("NO_TERM_ASSIGNMENTS");
                return result;
            }

            // Get the specific fee items
            List<TermFeeItem> feeItems = termFeeItemRepository.findAllById(feeItemIds);

            // Filter items belonging to the specific student
            List<TermFeeItem> studentFeeItems = feeItems.stream()
                    .filter(item -> item.getStudentTermAssignment() != null
                            && item.getStudentTermAssignment().getStudent() != null
                            && studentId.equals(item.getStudentTermAssignment().getStudent().getId()))
                    .collect(Collectors.toList());

            if (studentFeeItems.isEmpty()) {
                result.setValid(false);
                result.setMessage("No valid fee items found for student");
                result.setErrorCode("NO_VALID_ITEMS");
                return result;
            }

            // Check if any items are already paid
            List<TermFeeItem> unpaidItems = studentFeeItems.stream()
                    .filter(item -> item.getStatus() == TermFeeItem.FeeStatus.PENDING
                            || item.getStatus() == TermFeeItem.FeeStatus.PARTIAL
                            || item.getStatus() == TermFeeItem.FeeStatus.OVERDUE)
                    .collect(Collectors.toList());

            if (unpaidItems.isEmpty()) {
                result.setValid(false);
                result.setMessage("All selected fee items are already paid");
                result.setErrorCode("ITEMS_ALREADY_PAID");
                return result;
            }

            // Calculate pending amount
            double totalPending = unpaidItems.stream()
                    .mapToDouble(item -> {
                        if (item.getStatus() == TermFeeItem.FeeStatus.PARTIAL) {
                            return item.getAmount() - item.getPaidAmount();
                        }
                        return item.getAmount();
                    })
                    .sum();

            result.setValid(true);
            result.setTotalPendingAmount(totalPending);
            result.setUnpaidFeeItemCount((long) unpaidItems.size());
            result.setFeeItemCount((long) studentFeeItems.size());
            result.setMessage("Fee items are valid for payment");
            result.setErrorCode("ITEMS_VALID");

        } catch (Exception e) {
            log.error("Error validating fee items for student {}: {}", studentId, e.getMessage(), e);
            result.setValid(false);
            result.setMessage("Validation error: " + e.getMessage());
            result.setErrorCode("VALIDATION_ERROR");
        }

        return result;
    }

    /**
     * Validation result DTO
     */
    @Data
    public static class ValidationResult {
        private Long studentId;
        private String studentName;
        private boolean isValid;
        private String message;
        private String errorCode;
        private boolean hasTermAssignments;
        private Integer termAssignmentCount;
        private Long feeItemCount;
        private Long unpaidFeeItemCount;
        private Double totalPendingAmount;
        private boolean hasPendingAssignments;
        private Integer pendingAssignmentCount;
        private Set<Long> validFeeItemIds;

        public ValidationResult() {
            this.isValid = false;
            this.termAssignmentCount = 0;
            this.feeItemCount = 0L;
            this.unpaidFeeItemCount = 0L;
            this.totalPendingAmount = 0.0;
            this.pendingAssignmentCount = 0;
            this.validFeeItemIds = new HashSet<>();
        }
    }
}