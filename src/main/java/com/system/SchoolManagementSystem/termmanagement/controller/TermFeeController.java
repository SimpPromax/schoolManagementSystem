package com.system.SchoolManagementSystem.termmanagement.controller;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.termmanagement.dto.request.*;
import com.system.SchoolManagementSystem.termmanagement.dto.response.*;
import com.system.SchoolManagementSystem.termmanagement.entity.AcademicTerm;
import com.system.SchoolManagementSystem.termmanagement.entity.GradeTermFee;
import com.system.SchoolManagementSystem.termmanagement.entity.TermFeeItem;
import com.system.SchoolManagementSystem.termmanagement.service.TermFeeService;
import com.system.SchoolManagementSystem.termmanagement.service.TermService;
import com.system.SchoolManagementSystem.transaction.entity.PaymentTransaction;
import com.system.SchoolManagementSystem.transaction.enums.PaymentMethod;
import com.system.SchoolManagementSystem.transaction.repository.PaymentTransactionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@RestController
@RequestMapping("api/v1/fee-management")
@RequiredArgsConstructor
@Tag(name = "Term Fee Management", description = "Manage academic terms, fee structures, and billing")
public class TermFeeController {

    private final TermService termService;
    private final TermFeeService termFeeService;
    private final StudentRepository studentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    // ========== TERM MANAGEMENT ==========

    @PostMapping("/terms")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Create a new academic term")
    public ResponseEntity<?> createTerm(@Valid @RequestBody CreateTermRequest request) {
        try {
            AcademicTerm term = termService.createTerm(request);
            return ResponseEntity.ok(createSuccessResponse("Term created successfully", term));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/terms")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'TEACHER')")
    @Operation(summary = "Get all academic terms")
    public ResponseEntity<?> getAllTerms() {
        try {
            log.info("📊 Fetching all academic terms...");

            List<AcademicTerm> terms = termService.getAllTerms();
            log.info("✅ Found {} terms in database", terms.size());

            // Convert to response DTOs with null safety
            List<TermResponse> termResponses = new ArrayList<>();

            for (AcademicTerm term : terms) {
                try {
                    TermResponse response = new TermResponse();

                    // Set basic fields
                    response.setId(term.getId());
                    response.setTermName(term.getTermName());
                    response.setAcademicYear(term.getAcademicYear());
                    response.setTermCode(term.getTermCode());
                    response.setStartDate(term.getStartDate());
                    response.setEndDate(term.getEndDate());
                    response.setFeeDueDate(term.getFeeDueDate());

                    // Handle null status
                    if (term.getStatus() != null) {
                        response.setStatus(term.getStatus().name());
                    } else {
                        response.setStatus("UPCOMING");
                        log.warn("Term {} has null status, defaulting to UPCOMING", term.getId());
                    }

                    // Handle null isCurrent
                    response.setIsCurrent(term.getIsCurrent() != null ? term.getIsCurrent() : false);

                    response.setTermBreakDescription(term.getTermBreakDescription());
                    response.setTermBreaks(term.getTermBreakDates());
                    response.setCreatedAt(term.getCreatedAt());
                    response.setUpdatedAt(term.getUpdatedAt());

                    termResponses.add(response);

                } catch (Exception e) {
                    log.error("❌ Error converting term {} to response: {}", term.getId(), e.getMessage());
                    // Create a minimal response to avoid breaking the whole list
                    TermResponse minimalResponse = new TermResponse();
                    minimalResponse.setId(term.getId());
                    minimalResponse.setTermName(term.getTermName());
                    minimalResponse.setAcademicYear(term.getAcademicYear());
                    minimalResponse.setStartDate(term.getStartDate());
                    minimalResponse.setEndDate(term.getEndDate());
                    minimalResponse.setFeeDueDate(term.getFeeDueDate());
                    minimalResponse.setStatus("UPCOMING");
                    minimalResponse.setIsCurrent(false);
                    termResponses.add(minimalResponse);
                }
            }

            log.info("✅ Successfully converted {} terms to response DTOs", termResponses.size());

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("terms", termResponses);
            responseData.put("count", termResponses.size());
            responseData.put("currentTerm", termResponses.stream()
                    .filter(TermResponse::getIsCurrent)
                    .findFirst()
                    .orElse(null));

            return ResponseEntity.ok(createSuccessResponse("Terms retrieved successfully", responseData));

        } catch (Exception e) {
            log.error("❌ Error in getAllTerms endpoint: ", e);

            // Return error response with details
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            errorResponse.put("stackTrace", e.getStackTrace());
            errorResponse.put("timestamp", LocalDateTime.now());

            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Failed to retrieve terms: " + e.getMessage()));
        }
    }

    @GetMapping("/terms/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'TEACHER')")
    @Operation(summary = "Get current academic term")
    public ResponseEntity<?> getCurrentTerm() {
        try {
            return termService.getCurrentTerm()
                    .map(term -> ResponseEntity.ok(createSuccessResponse("Current term retrieved", term)))
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(createErrorResponse("No current term found")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/terms/years")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'TEACHER')")
    @Operation(summary = "Get all academic years")
    public ResponseEntity<?> getAcademicYears() {
        try {
            List<String> years = termService.getAllAcademicYears();
            return ResponseEntity.ok(createSuccessResponse("Academic years retrieved", years));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/terms/{termId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'TEACHER')")
    @Operation(summary = "Get term by ID")
    public ResponseEntity<?> getTermById(@PathVariable Long termId) {
        try {
            return termService.getTermById(termId)
                    .map(term -> ResponseEntity.ok(createSuccessResponse("Term retrieved", term)))
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(createErrorResponse("Term not found")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/terms/year/{academicYear}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'TEACHER')")
    @Operation(summary = "Get terms by academic year")
    public ResponseEntity<?> getTermsByAcademicYear(@PathVariable String academicYear) {
        try {
            List<AcademicTerm> terms = termService.getTermsByAcademicYear(academicYear);
            return ResponseEntity.ok(createSuccessResponse("Terms retrieved", terms));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @PutMapping("/terms/{termId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Update academic term")
    public ResponseEntity<?> updateTerm(
            @PathVariable Long termId,
            @Valid @RequestBody UpdateTermRequest request) {
        try {
            AcademicTerm updatedTerm = termService.updateTerm(termId, request);
            return ResponseEntity.ok(createSuccessResponse("Term updated successfully", updatedTerm));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @DeleteMapping("/terms/{termId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Delete academic term")
    public ResponseEntity<?> deleteTerm(@PathVariable Long termId) {
        try {
            boolean deleted = termService.deleteTerm(termId);
            if (deleted) {
                Map<String, Object> response = new HashMap<>();
                response.put("termId", termId);
                response.put("deleted", true);
                response.put("timestamp", LocalDateTime.now());
                return ResponseEntity.ok(createSuccessResponse("Term deleted successfully", response));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("Term not found or cannot be deleted"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/terms/{termId}/set-current")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Set term as current term")
    public ResponseEntity<?> setCurrentTerm(@PathVariable Long termId) {
        try {
            AcademicTerm term = termService.setCurrentTerm(termId);
            return ResponseEntity.ok(createSuccessResponse("Term set as current successfully", term));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    // ========== FEE STRUCTURE MANAGEMENT ==========

    @PostMapping("/fee-structure")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Create or update grade fee structure")
    public ResponseEntity<?> saveFeeStructure(@Valid @RequestBody FeeStructureRequest request) {
        try {
            GradeTermFee feeStructure = termFeeService.saveFeeStructure(request);
            return ResponseEntity.ok(createSuccessResponse("Fee structure saved", feeStructure));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/fee-structure/term/{termId}/grade/{grade}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'TEACHER')")
    @Operation(summary = "Get fee structure for grade in term")
    public ResponseEntity<?> getFeeStructure(@PathVariable Long termId, @PathVariable String grade) {
        try {
            return termFeeService.getFeeStructure(termId, grade)
                    .map(fee -> ResponseEntity.ok(createSuccessResponse("Fee structure retrieved", fee)))
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(createErrorResponse("No fee structure found")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/fee-structure/term/{termId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'TEACHER')")
    @Operation(summary = "Get all fee structures for term")
    public ResponseEntity<?> getFeeStructuresForTerm(@PathVariable Long termId) {
        try {
            List<GradeTermFee> feeStructures = termFeeService.getFeeStructuresForTerm(termId);
            return ResponseEntity.ok(createSuccessResponse("Fee structures retrieved", feeStructures));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/fee-structure/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'TEACHER')")
    @Operation(summary = "Get all active fee structures")
    public ResponseEntity<?> getActiveFeeStructures() {
        try {
            List<GradeTermFee> feeStructures = termFeeService.getActiveFeeStructures();
            return ResponseEntity.ok(createSuccessResponse("Active fee structures retrieved", feeStructures));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @PutMapping("/fee-structure/{structureId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Update fee structure")
    public ResponseEntity<?> updateFeeStructure(
            @PathVariable Long structureId,
            @Valid @RequestBody FeeStructureRequest request) {
        try {
            GradeTermFee updatedStructure = termFeeService.updateFeeStructure(structureId, request);
            return ResponseEntity.ok(createSuccessResponse("Fee structure updated successfully", updatedStructure));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @DeleteMapping("/fee-structure/{structureId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Delete fee structure")
    public ResponseEntity<?> deleteFeeStructure(@PathVariable Long structureId) {
        try {
            boolean deleted = termFeeService.deleteFeeStructure(structureId);
            if (deleted) {
                Map<String, Object> response = new HashMap<>();
                response.put("structureId", structureId);
                response.put("deleted", true);
                response.put("timestamp", LocalDateTime.now());
                return ResponseEntity.ok(createSuccessResponse("Fee structure deleted successfully", response));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("Fee structure not found or cannot be deleted"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @PutMapping("/fee-structure/{structureId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Update fee structure status")
    public ResponseEntity<?> updateFeeStructureStatus(
            @PathVariable Long structureId,
            @RequestParam Boolean isActive) {
        try {
            boolean updated = termFeeService.updateFeeStructureStatus(structureId, isActive);
            if (updated) {
                Map<String, Object> response = new HashMap<>();
                response.put("structureId", structureId);
                response.put("isActive", isActive);
                response.put("updated", true);
                response.put("timestamp", LocalDateTime.now());
                return ResponseEntity.ok(createSuccessResponse("Fee structure status updated", response));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("Fee structure not found"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    // ========== DASHBOARD & REPORTING ==========

    @GetMapping("/dashboard/grade/{grade}/term/{termId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'TEACHER')")
    @Operation(summary = "Get grade fee dashboard")
    public ResponseEntity<?> getGradeDashboard(@PathVariable String grade, @PathVariable Long termId) {
        try {
            GradeFeeDashboardResponse dashboard = termFeeService.getGradeFeeDashboard(grade, termId);
            return ResponseEntity.ok(createSuccessResponse("Dashboard data retrieved", dashboard));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/dashboard/school-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Get school-wide fee summary")
    public ResponseEntity<?> getSchoolFeeSummary() {
        try {
            SchoolFeeSummary summary = termFeeService.getSchoolFeeSummary();
            return ResponseEntity.ok(createSuccessResponse("School fee summary retrieved", summary));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    // ========== ADDITIONAL FEE MANAGEMENT ==========

    @PostMapping("/additional-fees")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Add additional fee for student")
    public ResponseEntity<?> addAdditionalFee(@Valid @RequestBody AdditionalFeeRequestDto request) {
        try {
            TermFeeItem feeItem = termFeeService.addAdditionalFeeItem(request);
            return ResponseEntity.ok(createSuccessResponse("Additional fee added", feeItem));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/additional-fees/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Add additional fees to multiple students")
    public ResponseEntity<?> bulkAddAdditionalFees(@Valid @RequestBody BulkFeeRequest request) {
        try {
            BulkFeeResult result = termFeeService.bulkAddAdditionalFees(request);
            return ResponseEntity.ok(createSuccessResponse(
                    String.format("Added fees to %d of %d students",
                            result.getSuccessCount(), result.getTotalStudents()),
                    result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/students/{studentId}/additional-fees")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'TEACHER')")
    @Operation(summary = "Get additional fees for student")
    public ResponseEntity<?> getStudentAdditionalFees(@PathVariable Long studentId) {
        try {
            List<TermFeeItem> additionalFees = termFeeService.getStudentAdditionalFees(studentId);
            return ResponseEntity.ok(createSuccessResponse("Additional fees retrieved", additionalFees));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @DeleteMapping("/additional-fees/{feeItemId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Delete additional fee item")
    public ResponseEntity<?> deleteAdditionalFee(@PathVariable Long feeItemId) {
        try {
            boolean deleted = termFeeService.deleteAdditionalFee(feeItemId);
            if (deleted) {
                Map<String, Object> response = new HashMap<>();
                response.put("feeItemId", feeItemId);
                response.put("deleted", true);
                response.put("timestamp", LocalDateTime.now());
                return ResponseEntity.ok(createSuccessResponse("Additional fee deleted successfully", response));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("Fee item not found or cannot be deleted"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    // ========== PAYMENT APPLICATION ==========

    @PostMapping("/payments/apply")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Apply payment to student's fee items")
    public ResponseEntity<?> applyPayment(@Valid @RequestBody PaymentApplicationRequest request) {
        try {
            PaymentApplicationResponse response = termFeeService.applyPaymentToStudent(request);
            return ResponseEntity.ok(createSuccessResponse("Payment applied successfully", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    // ========== AUTO-BILLING ==========

    @PostMapping("/auto-bill")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Trigger auto-billing for current term")
    public ResponseEntity<?> triggerAutoBilling() {
        log.info("🎯 Manual auto-billing triggered via API");

        try {
            log.debug("Getting current term for auto-billing...");
            AutoBillingResult result = termFeeService.autoBillCurrentTerm();

            log.info("Auto-billing result: {} billed, {} skipped, {} errors",
                    result.getBilledCount(), result.getSkippedCount(), result.getErrors().size());

            // Create detailed response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", result.isSuccess());
            responseData.put("message", result.getMessage());
            responseData.put("billedCount", result.getBilledCount());
            responseData.put("skippedCount", result.getSkippedCount());
            responseData.put("errorCount", result.getErrors().size());
            responseData.put("termName", result.getTermName());
            responseData.put("academicYear", result.getAcademicYear());
            responseData.put("timestamp", LocalDateTime.now());

            if (!result.getErrors().isEmpty()) {
                responseData.put("errors", result.getErrors().subList(0, Math.min(10, result.getErrors().size())));
                responseData.put("totalErrors", result.getErrors().size());
            }

            return ResponseEntity.ok(createSuccessResponse("Auto-billing triggered", responseData));

        } catch (Exception e) {
            log.error("❌ API auto-billing failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Auto-billing failed: " + e.getMessage()));
        }
    }

    @PostMapping("/auto-bill/student/{studentId}/term/{termId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Auto-bill specific student for term")
    public ResponseEntity<?> autoBillStudent(@PathVariable Long studentId, @PathVariable Long termId) {
        try {
            boolean success = termFeeService.autoBillStudentForTerm(studentId, termId);
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", success);
            responseData.put("message", success ?
                    "Student successfully auto-billed" :
                    "Student already billed or no fee structure available");

            return ResponseEntity.ok(createSuccessResponse(
                    success ? "Student auto-billed successfully" : "Auto-billing skipped",
                    responseData));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    // ========== REPORTING & STATISTICS ==========

    @GetMapping("/students/{studentId}/payment-history")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'TEACHER', 'PARENT')")
    @Operation(summary = "Get student's payment history")
    public ResponseEntity<?> getStudentPaymentHistory(@PathVariable Long studentId) {
        try {
            PaymentHistoryResponse history = termFeeService.getStudentPaymentHistory(studentId);
            return ResponseEntity.ok(createSuccessResponse("Payment history retrieved successfully", history));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/reports/overdue-fees")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Get overdue fees report")
    public ResponseEntity<?> getOverdueFeesReport() {
        try {
            OverdueFeesReport report = termFeeService.getOverdueFeesReport();
            return ResponseEntity.ok(createSuccessResponse("Overdue fees report generated successfully", report));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/reports/collection-summary/{startDate}/{endDate}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Get collection summary for date range")
    public ResponseEntity<?> getCollectionSummary(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            CollectionSummaryResponse summary = termFeeService.getCollectionSummary(startDate, endDate);
            return ResponseEntity.ok(createSuccessResponse("Collection summary retrieved successfully", summary));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/bulk/update-fee-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Bulk update fee status for multiple students")
    public ResponseEntity<?> bulkUpdateFeeStatus(@Valid @RequestBody BulkUpdateRequest request) {
        try {
            BulkUpdateResult result = termFeeService.bulkUpdateFeeStatus(request);
            return ResponseEntity.ok(createSuccessResponse("Fee status updated for students", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/bulk/send-reminders")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Send fee reminders to multiple students")
    public ResponseEntity<?> bulkSendReminders(@Valid @RequestBody ReminderRequest request) {
        try {
            ReminderResult result = termFeeService.bulkSendReminders(request);
            return ResponseEntity.ok(createSuccessResponse("Reminders sent successfully", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/terms/{termId}/fee-statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Get fee statistics for term")
    public ResponseEntity<?> getTermFeeStatistics(@PathVariable Long termId) {
        try {
            TermFeeStatistics statistics = termFeeService.getTermFeeStatistics(termId);
            return ResponseEntity.ok(createSuccessResponse("Term fee statistics retrieved successfully", statistics));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/students/{studentId}/term/{termId}/regenerate-bill")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Regenerate bill for student in term")
    public ResponseEntity<?> regenerateStudentBill(
            @PathVariable Long studentId,
            @PathVariable Long termId) {
        try {
            RegenerateBillResponse response = termFeeService.regenerateStudentBill(studentId, termId);
            return ResponseEntity.ok(createSuccessResponse("Bill regenerated successfully", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    // ========== STUDENT FEE DETAILS ==========

    @GetMapping("/students/{studentId}/term-assignments")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'TEACHER', 'PARENT')")
    @Operation(summary = "Get student's term fee assignments")
    public ResponseEntity<?> getStudentTermAssignments(@PathVariable Long studentId) {
        try {
            List<com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment> assignments =
                    termFeeService.getStudentTermAssignments(studentId);

            // Convert to enhanced DTO
            StudentFeeDetailsResponse response = createStudentFeeDetailsResponse(studentId, assignments);
            return ResponseEntity.ok(createSuccessResponse("Student term assignments retrieved", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    // ========== HELPER METHODS FOR DTO MAPPING ==========

    private StudentFeeDetailsResponse createStudentFeeDetailsResponse(
            Long studentId,
            List<com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment> assignments) {

        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            throw new RuntimeException("Student not found: " + studentId);
        }

        StudentFeeDetailsResponse response = new StudentFeeDetailsResponse();
        response.setStudentId(studentId);
        response.setStudentName(student.getFullName());
        response.setStudentCode(student.getStudentId());
        response.setGrade(student.getGrade());
        response.setClassName(student.getGrade());

        // Calculate overall summary
        double totalFee = assignments.stream()
                .mapToDouble(com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment::getTotalTermFee)
                .sum();
        double paidAmount = assignments.stream()
                .mapToDouble(com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment::getPaidAmount)
                .sum();
        double pendingAmount = totalFee - paidAmount;
        double paymentPercentage = totalFee > 0 ? (paidAmount / totalFee) * 100 : 0;

        response.setTotalFee(totalFee);
        response.setPaidAmount(paidAmount);
        response.setPendingAmount(pendingAmount);
        response.setPaymentPercentage(paymentPercentage);
        response.setFeeStatus(calculateOverallFeeStatus(assignments));

        // Get current term info
        termService.getCurrentTerm().ifPresent(currentTerm -> {
            response.setCurrentTermId(currentTerm.getId());
            response.setCurrentTermName(currentTerm.getTermName());
            response.setAcademicYear(currentTerm.getAcademicYear());
            response.setFeeDueDate(currentTerm.getFeeDueDate());
        });

        // Create term summaries
        List<StudentFeeDetailsResponse.TermFeeSummary> termSummaries = assignments.stream()
                .map(this::mapToTermFeeSummary)
                .collect(Collectors.toList());
        response.setTermSummaries(termSummaries);

        // Get current term fee items
        termService.getCurrentTerm().ifPresent(currentTerm -> {
            assignments.stream()
                    .filter(a -> a.getAcademicTerm().getId().equals(currentTerm.getId()))
                    .findFirst()
                    .ifPresent(currentAssignment -> {
                        List<StudentFeeDetailsResponse.TermFeeItemResponse> feeItems =
                                currentAssignment.getFeeItems().stream()
                                        .map(this::mapToTermFeeItemResponse)
                                        .collect(Collectors.toList());
                        response.setFeeItems(feeItems);
                    });
        });

        // Add payment summary
        List<PaymentTransaction> transactions = paymentTransactionRepository
                .findByStudentIdOrderByPaymentDateDesc(studentId);
        if (!transactions.isEmpty()) {
            StudentFeeDetailsResponse.PaymentSummary paymentSummary =
                    new StudentFeeDetailsResponse.PaymentSummary();
            paymentSummary.setTotalPayments(transactions.size());
            paymentSummary.setTotalAmountPaid(transactions.stream()
                    .mapToDouble(PaymentTransaction::getAmount)
                    .sum());
            paymentSummary.setLastPaymentDate(transactions.get(0).getPaymentDate().toLocalDate());
            paymentSummary.setLastPaymentMethod(transactions.get(0).getPaymentMethod() != null ?
                    transactions.get(0).getPaymentMethod().name() : "UNKNOWN");
            paymentSummary.setLastPaymentAmount(transactions.get(0).getAmount());
            paymentSummary.setLastPaymentReference(transactions.get(0).getBankReference());
            response.setPaymentSummary(paymentSummary);
        }

        // Add guardian info using emergency contact
        StudentFeeDetailsResponse.GuardianInfo guardianInfo =
                new StudentFeeDetailsResponse.GuardianInfo();
        guardianInfo.setGuardianName(student.getEmergencyContactName());
        guardianInfo.setRelationship(student.getEmergencyRelation());
        guardianInfo.setPhoneNumber(student.getEmergencyContactPhone());
        guardianInfo.setEmail(student.getEmail());
        guardianInfo.setAddress(student.getAddress());
        response.setGuardianInfo(guardianInfo);

        // Add reminder info
        termFeeService.getStudentTermAssignments(studentId).stream()
                .filter(assignment -> assignment.getAcademicTerm() != null &&
                        assignment.getAcademicTerm().getIsCurrent() != null &&
                        assignment.getAcademicTerm().getIsCurrent())
                .findFirst()
                .ifPresent(assignment -> {
                    StudentFeeDetailsResponse.ReminderInfo reminderInfo =
                            new StudentFeeDetailsResponse.ReminderInfo();
                    reminderInfo.setRemindersSent(assignment.getRemindersSent());
                    reminderInfo.setLastReminderDate(assignment.getLastReminderDate());
                    reminderInfo.setLastReminderType("SMS/EMAIL");
                    if (assignment.getDueDate() != null) {
                        long overdueDays = LocalDate.now().toEpochDay() - assignment.getDueDate().toEpochDay();
                        reminderInfo.setOverdueDays(Math.max(0, (int) overdueDays));
                    }
                    response.setReminderInfo(reminderInfo);
                });

        return response;
    }

    private StudentFeeDetailsResponse.TermFeeSummary mapToTermFeeSummary(
            com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment assignment) {

        StudentFeeDetailsResponse.TermFeeSummary summary =
                new StudentFeeDetailsResponse.TermFeeSummary();
        summary.setTermId(assignment.getAcademicTerm().getId());
        summary.setTermName(assignment.getAcademicTerm().getTermName());
        summary.setTotalFee(assignment.getTotalTermFee());
        summary.setPaidAmount(assignment.getPaidAmount());
        summary.setPendingAmount(assignment.getPendingAmount());
        summary.setStatus(assignment.getTermFeeStatus().name());
        summary.setDueDate(assignment.getDueDate());
        summary.setIsCurrent(assignment.getAcademicTerm().getIsCurrent());

        return summary;
    }

    private StudentFeeDetailsResponse.TermFeeItemResponse mapToTermFeeItemResponse(TermFeeItem item) {
        StudentFeeDetailsResponse.TermFeeItemResponse response =
                new StudentFeeDetailsResponse.TermFeeItemResponse();
        response.setId(item.getId());
        response.setItemName(item.getItemName());
        response.setFeeType(item.getFeeType().name());
        response.setItemType(item.getItemType());
        response.setAmount(item.getAmount());
        response.setPaidAmount(item.getPaidAmount());
        response.setPendingAmount(item.getPendingAmount());
        response.setStatus(item.getStatus().name());
        response.setDueDate(item.getDueDate());
        response.setBillingDate(item.getBillingDate());
        response.setIsAutoGenerated(item.getIsAutoGenerated());
        response.setIsMandatory(item.getIsMandatory());
        response.setSequenceOrder(item.getSequenceOrder());
        response.setNotes(item.getNotes());

        return response;
    }

    private String calculateOverallFeeStatus(
            List<com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment> assignments) {
        if (assignments.isEmpty()) {
            return "NOT_ASSIGNED";
        }

        boolean allPaid = assignments.stream()
                .allMatch(a -> a.getTermFeeStatus() ==
                        com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment.FeeStatus.PAID);

        boolean anyOverdue = assignments.stream()
                .anyMatch(a -> a.getTermFeeStatus() ==
                        com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment.FeeStatus.OVERDUE);

        boolean anyPartial = assignments.stream()
                .anyMatch(a -> a.getTermFeeStatus() ==
                        com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment.FeeStatus.PARTIAL);

        if (allPaid) return "PAID";
        if (anyOverdue) return "OVERDUE";
        if (anyPartial) return "PARTIAL";
        return "PENDING";
    }

    // ========== HELPER METHODS ==========

    private Map<String, Object> createSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    private Map<String, Object> createErrorResponse(String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", error);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    // ========== HEALTH CHECK ==========

    @GetMapping("/health")
    @Operation(summary = "Health check for term fee management")
    public ResponseEntity<?> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("service", "Term Fee Management");
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("currentTerm", termService.getCurrentTerm().isPresent() ? "Available" : "Not set");

        try {
            long termCount = termService.getAllTerms().size();
            health.put("totalTerms", termCount);
            health.put("message", "Service is healthy");

            return ResponseEntity.ok(createSuccessResponse("Service is healthy", health));
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Service health check failed: " + e.getMessage()));
        }
    }

    // In TermFeeController.java
    @GetMapping("/grades")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'TEACHER')")
    @Operation(summary = "Get all distinct grades from students")
    public ResponseEntity<?> getAllGrades() {
        try {
            List<String> grades = studentRepository.findDistinctGrades();
            return ResponseEntity.ok(createSuccessResponse("Grades retrieved successfully", grades));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }


}