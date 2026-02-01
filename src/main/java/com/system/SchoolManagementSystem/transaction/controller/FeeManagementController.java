package com.system.SchoolManagementSystem.transaction.controller;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment;
import com.system.SchoolManagementSystem.termmanagement.entity.TermFeeItem;
import com.system.SchoolManagementSystem.termmanagement.dto.request.PaymentApplicationRequest;
import com.system.SchoolManagementSystem.termmanagement.dto.response.PaymentApplicationResponse;
import com.system.SchoolManagementSystem.transaction.service.StudentFeeUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fees")
@RequiredArgsConstructor
@Tag(name = "Fee Management", description = "Endpoints for managing student fees")
public class FeeManagementController {

    private final StudentFeeUpdateService feeUpdateService;
    private final StudentRepository studentRepository;

    @GetMapping("/student/{studentId}/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'TEACHER')")
    @Operation(summary = "Get fee summary for a student")
    public ResponseEntity<?> getFeeSummary(@PathVariable Long studentId) {
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            StudentFeeUpdateService.StudentFeeSummary summary = feeUpdateService.getStudentFeeSummary(studentId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", summary);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get fee summary: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/student/{studentId}/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Force refresh student fee data")
    public ResponseEntity<?> refreshStudentFeeData(@PathVariable Long studentId) {
        try {
            feeUpdateService.refreshStudentFeeData(studentId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Student fee data refreshed successfully");
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to refresh fee data: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/student/{studentId}/manual-payment")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Apply manual payment to student")
    public ResponseEntity<?> applyManualPayment(
            @PathVariable Long studentId,
            @RequestParam Double amount,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) String notes) {

        try {
            PaymentApplicationResponse paymentResult = feeUpdateService.applyManualPayment(
                    studentId,
                    amount,
                    reference != null ? reference : "MANUAL_" + System.currentTimeMillis(),
                    notes != null ? notes : "Manual payment via API"
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("Manual payment of ₹%.2f applied successfully", amount));
            response.put("data", Map.of(
                    "studentId", studentId,
                    "appliedAmount", paymentResult.getAppliedPayment(),
                    "remainingPayment", paymentResult.getRemainingPayment(),
                    "allPaid", paymentResult.getAllPaid(),
                    "appliedItems", paymentResult.getAppliedItems()
            ));
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to apply manual payment: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/student/{studentId}/revert-payment")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Revert a payment from student")
    public ResponseEntity<?> revertPayment(
            @PathVariable Long studentId,
            @RequestParam Double amount,
            @RequestParam String originalReference,
            @RequestParam String reason) {

        try {
            PaymentApplicationResponse revertResult = feeUpdateService.revertPayment(
                    studentId,
                    amount,
                    originalReference,
                    reason
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("Payment of ₹%.2f reverted successfully", amount));
            response.put("data", Map.of(
                    "studentId", studentId,
                    "revertedAmount", amount,
                    "reason", reason,
                    "result", revertResult
            ));
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to revert payment: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/student/{studentId}/validate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Validate if student can receive payment")
    public ResponseEntity<?> validateStudentForPayment(@PathVariable Long studentId) {
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));

            // Validate student
            if (student.getStatus() != Student.StudentStatus.ACTIVE) {
                throw new RuntimeException("Student is not active. Status: " + student.getStatus());
            }

            if (student.getTermAssignments().isEmpty()) {
                throw new RuntimeException("Student has no term assignments");
            }

            // Get detailed term info
            List<Map<String, Object>> termInfo = new ArrayList<>();
            for (StudentTermAssignment ta : student.getTermAssignments()) {
                Map<String, Object> termData = new HashMap<>();
                if (ta.getAcademicTerm() != null) {
                    termData.put("termId", ta.getAcademicTerm().getId());
                    termData.put("termName", ta.getAcademicTerm().getTermName());
                } else {
                    termData.put("termId", null);
                    termData.put("termName", "Unnamed Term");
                }
                termData.put("totalFee", ta.getTotalTermFee() != null ? ta.getTotalTermFee() : 0.0);
                termData.put("paidAmount", ta.getPaidAmount() != null ? ta.getPaidAmount() : 0.0);
                termData.put("pendingAmount", ta.getPendingAmount() != null ? ta.getPendingAmount() : 0.0);
                termData.put("status", ta.getTermFeeStatus() != null ? ta.getTermFeeStatus().toString() : "UNASSIGNED");
                termInfo.add(termData);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("Student %s is eligible for payment", student.getFullName()));
            response.put("data", Map.of(
                    "studentId", studentId,
                    "studentName", student.getFullName(),
                    "grade", student.getGrade(),
                    "status", student.getStatus(),
                    "hasTermAssignments", !student.getTermAssignments().isEmpty(),
                    "termAssignmentCount", student.getTermAssignments().size(),
                    "termInfo", termInfo,
                    "pendingAmount", student.getPendingAmount(),
                    "totalFee", student.getTotalFee(),
                    "feeStatus", student.getFeeStatus()
            ));
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Student validation failed: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());
            errorResponse.put("studentId", studentId);

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/student/{studentId}/term-assignments")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'TEACHER')")
    @Operation(summary = "Check student term assignments")
    public ResponseEntity<?> getStudentTermAssignments(@PathVariable Long studentId) {
        try {
            // Use the repository method with fee items fetch
            Student student = studentRepository.findByIdWithTermAssignmentsAndFeeItems(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));

            boolean hasTermAssignments = !student.getTermAssignments().isEmpty();
            int termAssignmentCount = student.getTermAssignments().size();

            // Create detailed term assignments response
            List<Map<String, Object>> termAssignments = new ArrayList<>();

            for (StudentTermAssignment ta : student.getTermAssignments()) {
                Map<String, Object> termData = new HashMap<>();

                // Basic term info
                if (ta.getAcademicTerm() != null) {
                    termData.put("termId", ta.getAcademicTerm().getId());
                    termData.put("termName", ta.getAcademicTerm().getTermName());
                    termData.put("academicYear", ta.getAcademicTerm().getAcademicYear());
                    termData.put("isCurrent", ta.getAcademicTerm().getIsCurrent());
                } else {
                    termData.put("termId", null);
                    termData.put("termName", "Unnamed Term");
                    termData.put("academicYear", null);
                    termData.put("isCurrent", false);
                }

                termData.put("assignmentId", ta.getId());
                termData.put("totalFee", ta.getTotalTermFee() != null ? ta.getTotalTermFee() : 0.0);
                termData.put("paidAmount", ta.getPaidAmount() != null ? ta.getPaidAmount() : 0.0);
                termData.put("pendingAmount", ta.getPendingAmount() != null ? ta.getPendingAmount() : 0.0);
                termData.put("status", ta.getTermFeeStatus() != null ? ta.getTermFeeStatus().toString() : "UNASSIGNED");
                termData.put("dueDate", ta.getDueDate());
                termData.put("isBilled", ta.getIsBilled() != null ? ta.getIsBilled() : false);
                termData.put("billingDate", ta.getBillingDate());
                termData.put("lastPaymentDate", ta.getLastPaymentDate());
                termData.put("createdAt", ta.getCreatedAt());
                termData.put("updatedAt", ta.getUpdatedAt());

                // Get TermFeeItems - CORRECTED: It's a List, not a Set
                List<Map<String, Object>> feeItems = new ArrayList<>();
                double tuitionFee = 0.0;
                double admissionFee = 0.0;
                double examinationFee = 0.0;
                double transportFee = 0.0;
                double libraryFee = 0.0;
                double sportsFee = 0.0;
                double activityFee = 0.0;
                double hostelFee = 0.0;
                double uniformFee = 0.0;
                double bookFee = 0.0;
                double otherFees = 0.0;
                double lateFee = 0.0;
                double discount = 0.0;

                // CORRECTED: ta.getFeeItems() returns List<TermFeeItem>
                List<TermFeeItem> termFeeItems = ta.getFeeItems();
                if (termFeeItems != null && !termFeeItems.isEmpty()) {
                    for (TermFeeItem tfi : termFeeItems) {
                        Map<String, Object> feeItem = new HashMap<>();
                        feeItem.put("id", tfi.getId());
                        feeItem.put("itemName", tfi.getItemName());
                        feeItem.put("itemType", tfi.getItemType());
                        feeItem.put("feeType", tfi.getFeeType() != null ? tfi.getFeeType().toString() : "OTHER");
                        feeItem.put("description", tfi.getNotes());
                        feeItem.put("amount", tfi.getAmount() != null ? tfi.getAmount() : 0.0);
                        feeItem.put("originalAmount", tfi.getOriginalAmount() != null ? tfi.getOriginalAmount() : tfi.getAmount());
                        feeItem.put("paidAmount", tfi.getPaidAmount() != null ? tfi.getPaidAmount() : 0.0);
                        feeItem.put("pendingAmount", tfi.getPendingAmount() != null ? tfi.getPendingAmount() : 0.0);
                        feeItem.put("status", tfi.getStatus() != null ? tfi.getStatus().toString() : "PENDING");
                        feeItem.put("dueDate", tfi.getDueDate());
                        feeItem.put("billingDate", tfi.getBillingDate());
                        feeItem.put("paidDate", tfi.getPaidDate());
                        feeItem.put("isMandatory", tfi.getIsMandatory() != null ? tfi.getIsMandatory() : true);
                        feeItem.put("isAutoGenerated", tfi.getIsAutoGenerated() != null ? tfi.getIsAutoGenerated() : false);
                        feeItem.put("sequenceOrder", tfi.getSequenceOrder());

                        feeItems.add(feeItem);

                        // Categorize fees for breakdown - USING TermFeeItem.FeeType enum
                        if (tfi.getFeeType() != null) {
                            String feeType = tfi.getFeeType().toString();
                            double amount = tfi.getAmount() != null ? tfi.getAmount() : 0.0;

                            // Handle negative amounts for discounts
                            if (amount < 0) {
                                discount += Math.abs(amount);
                                continue;
                            }

                            switch (feeType) {
                                case "TUITION":
                                    tuitionFee += amount;
                                    break;
                                case "ADMISSION":
                                    admissionFee += amount;
                                    break;
                                case "EXAMINATION":
                                    examinationFee += amount;
                                    break;
                                case "TRANSPORT":
                                    transportFee += amount;
                                    break;
                                case "LIBRARY":
                                    libraryFee += amount;
                                    break;
                                case "SPORTS":
                                    sportsFee += amount;
                                    break;
                                case "ACTIVITY":
                                    activityFee += amount;
                                    break;
                                case "HOSTEL":
                                    hostelFee += amount;
                                    break;
                                case "UNIFORM":
                                    uniformFee += amount;
                                    break;
                                case "BOOKS":
                                    bookFee += amount;
                                    break;
                                case "LATE_FEE":
                                    lateFee += amount;
                                    break;
                                case "DISCOUNT":
                                    discount += amount;
                                    break;
                                default: // OTHER, BASIC
                                    otherFees += amount;
                                    break;
                            }
                        }
                    }
                }

                termData.put("feeItems", feeItems);
                termData.put("feeItemCount", feeItems.size());

                // Add detailed fee breakdown
                Map<String, Double> feeBreakdown = new HashMap<>();
                feeBreakdown.put("tuitionFee", tuitionFee);
                feeBreakdown.put("admissionFee", admissionFee);
                feeBreakdown.put("examinationFee", examinationFee);
                feeBreakdown.put("transportFee", transportFee);
                feeBreakdown.put("libraryFee", libraryFee);
                feeBreakdown.put("sportsFee", sportsFee);
                feeBreakdown.put("activityFee", activityFee);
                feeBreakdown.put("hostelFee", hostelFee);
                feeBreakdown.put("uniformFee", uniformFee);
                feeBreakdown.put("bookFee", bookFee);
                feeBreakdown.put("otherFees", otherFees);
                feeBreakdown.put("lateFee", lateFee);
                feeBreakdown.put("discount", discount);

                termData.put("feeBreakdown", feeBreakdown);

                // Calculate payment progress percentage
                double totalFee = ta.getTotalTermFee() != null ? ta.getTotalTermFee() : 0.0;
                double paidAmount = ta.getPaidAmount() != null ? ta.getPaidAmount() : 0.0;
                double progressPercentage = totalFee > 0 ? (paidAmount / totalFee) * 100 : 0.0;
                termData.put("progressPercentage", Math.round(progressPercentage));

                // Check if overdue
                boolean isOverdue = ta.getDueDate() != null &&
                        ta.getDueDate().isBefore(java.time.LocalDate.now()) &&
                        ta.getPendingAmount() != null &&
                        ta.getPendingAmount() > 0;
                termData.put("isOverdue", isOverdue);

                termAssignments.add(termData);
            }

            // Calculate overall student fee summary
            Map<String, Object> studentFeeSummary = new HashMap<>();
            studentFeeSummary.put("totalFee", student.getTotalFee() != null ? student.getTotalFee() : 0.0);
            studentFeeSummary.put("paidAmount", student.getPaidAmount() != null ? student.getPaidAmount() : 0.0);
            studentFeeSummary.put("pendingAmount", student.getPendingAmount() != null ? student.getPendingAmount() : 0.0);
            studentFeeSummary.put("tuitionFee", student.getTuitionFee() != null ? student.getTuitionFee() : 0.0);
            studentFeeSummary.put("admissionFee", student.getAdmissionFee() != null ? student.getAdmissionFee() : 0.0);
            studentFeeSummary.put("examinationFee", student.getExaminationFee() != null ? student.getExaminationFee() : 0.0);
            studentFeeSummary.put("otherFees", student.getOtherFees() != null ? student.getOtherFees() : 0.0);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", hasTermAssignments ?
                    "Student has " + termAssignmentCount + " term assignment(s)" :
                    "Student has NO term assignments");
            response.put("data", Map.of(
                    "studentId", studentId,
                    "studentName", student.getFullName(),
                    "grade", student.getGrade(),
                    "studentCode", student.getStudentId(),
                    "hasTermAssignments", hasTermAssignments,
                    "termAssignmentCount", termAssignmentCount,
                    "feeSummary", studentFeeSummary,
                    "feeStatus", student.getFeeStatus() != null ? student.getFeeStatus().toString() : "PENDING",
                    "termAssignments", termAssignments
            ));
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get term assignments: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());
            errorResponse.put("studentId", studentId);

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/student/{studentId}/simulate-payment")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Simulate payment without actual transaction (for testing)")
    public ResponseEntity<?> simulatePayment(
            @PathVariable Long studentId,
            @RequestBody PaymentApplicationRequest request) {

        try {
            // Create a simulation request
            PaymentApplicationRequest simulationRequest = new PaymentApplicationRequest();
            simulationRequest.setStudentId(studentId);
            simulationRequest.setAmount(request.getAmount());
            simulationRequest.setReference("SIM_" + System.currentTimeMillis());
            simulationRequest.setNotes("Simulation: " + (request.getNotes() != null ? request.getNotes() : "Test payment"));
            simulationRequest.setApplyToFutureTerms(request.getApplyToFutureTerms());

            // In a real scenario, you would call termFeeService directly for simulation
            // For now, return mock response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment simulation completed");
            response.put("data", Map.of(
                    "studentId", studentId,
                    "simulatedAmount", request.getAmount(),
                    "reference", simulationRequest.getReference(),
                    "notes", simulationRequest.getNotes(),
                    "warning", "This is a simulation - no actual payment was processed"
            ));
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Simulation failed: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/student/{studentId}/payment-history")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Get student payment history summary")
    public ResponseEntity<?> getPaymentHistory(@PathVariable Long studentId) {
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            StudentFeeUpdateService.StudentFeeSummary summary = feeUpdateService.getStudentFeeSummary(studentId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", Map.of(
                    "studentInfo", Map.of(
                            "id", student.getId(),
                            "name", student.getFullName(),
                            "grade", student.getGrade(),
                            "studentId", student.getStudentId()
                    ),
                    "feeSummary", summary,
                    "paymentProgress", summary.getPaymentProgress(),
                    "isEligibleForPayment", student.getPendingAmount() != null && student.getPendingAmount() > 0
            ));
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get payment history: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}