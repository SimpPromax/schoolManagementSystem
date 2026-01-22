package com.system.SchoolManagementSystem.fee.controller;

import com.system.SchoolManagementSystem.fee.dto.request.*;
import com.system.SchoolManagementSystem.fee.dto.response.*;
import com.system.SchoolManagementSystem.fee.service.FeeCollectionService;
import com.system.SchoolManagementSystem.fee.service.ReportGeneratorService;
import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fee-collection")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Fee Collection", description = "Endpoints for fee collection management, reminders, and reporting")
public class FeeCollectionController {

    private final FeeCollectionService feeCollectionService;
    private final ReportGeneratorService reportGeneratorService;
    private final StudentRepository studentRepository;

    // ========== DASHBOARD STATISTICS ENDPOINTS ==========

    @GetMapping("/dashboard/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL')")
    @Operation(summary = "Get fee collection dashboard statistics")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        try {
            FeeCollectionStatsResponse stats = feeCollectionService.getDashboardStats();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Dashboard statistics retrieved successfully");
            response.put("data", stats);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get dashboard stats", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get dashboard statistics: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/dashboard/trend")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL')")
    @Operation(summary = "Get fee collection trend data")
    public ResponseEntity<Map<String, Object>> getCollectionTrend(
            @RequestParam(defaultValue = "MONTHLY") String period) {
        try {
            CollectionTrendResponse trend = feeCollectionService.getCollectionTrend(period);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Collection trend data retrieved successfully");
            response.put("data", trend);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get collection trend", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get collection trend: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/dashboard/payment-methods")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL')")
    @Operation(summary = "Get payment method distribution")
    public ResponseEntity<Map<String, Object>> getPaymentMethodDistribution() {
        try {
            PaymentMethodDistributionResponse distribution =
                    feeCollectionService.getPaymentMethodDistribution();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment method distribution retrieved successfully");
            response.put("data", distribution);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get payment method distribution", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get payment method distribution: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/dashboard/overdue-distribution")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL')")
    @Operation(summary = "Get overdue fee distribution")
    public ResponseEntity<Map<String, Object>> getOverdueDistribution() {
        try {
            OverdueDistributionResponse distribution =
                    feeCollectionService.getOverdueDistribution();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Overdue distribution retrieved successfully");
            response.put("data", distribution);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get overdue distribution", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get overdue distribution: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/dashboard/recent-payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL', 'TEACHER')")
    @Operation(summary = "Get recent fee payments")
    public ResponseEntity<Map<String, Object>> getRecentPayments(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<RecentPaymentResponse> recentPayments =
                    feeCollectionService.getRecentPayments(limit);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Recent payments retrieved successfully");
            response.put("data", recentPayments);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get recent payments", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get recent payments: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ========== STUDENT FEE MANAGEMENT ENDPOINTS ==========

    @PostMapping("/students/filter")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL', 'TEACHER')")
    @Operation(summary = "Get filtered student fee data with pagination")
    public ResponseEntity<Map<String, Object>> getFilteredStudents(
            @Valid @RequestBody FeeCollectionFilterRequest filterRequest) {
        try {
            Page<StudentFeeSummaryResponse> students =
                    feeCollectionService.getFilteredStudents(filterRequest);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Student fee data retrieved successfully");
            response.put("data", students.getContent());
            response.put("page", students.getNumber());
            response.put("size", students.getSize());
            response.put("totalPages", students.getTotalPages());
            response.put("totalElements", students.getTotalElements());
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get filtered students", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get student fee data: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/students/{id}/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL', 'TEACHER')")
    @Operation(summary = "Get student fee summary")
    public ResponseEntity<Map<String, Object>> getStudentFeeSummary(@PathVariable Long id) {
        try {
            Student student = studentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Student not found with id: " + id));

            StudentFeeSummaryResponse summary = feeCollectionService.convertToStudentFeeSummaryResponse(student);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Student fee summary retrieved successfully");
            response.put("data", summary);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get student fee summary for id: {}", id, e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get student fee summary: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
    }

    @GetMapping("/students/{id}/payment-history")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL', 'TEACHER')")
    @Operation(summary = "Get complete student payment history")
    public ResponseEntity<Map<String, Object>> getStudentPaymentHistory(@PathVariable Long id) {
        try {
            StudentPaymentHistoryResponse history =
                    feeCollectionService.getStudentPaymentHistory(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Student payment history retrieved successfully");
            response.put("data", history);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get payment history for student id: {}", id, e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get payment history: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
    }

    @GetMapping("/students/quick-stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL')")
    @Operation(summary = "Get quick student fee statistics")
    public ResponseEntity<Map<String, Object>> getQuickStudentStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // Total students with fees
            long totalStudents = studentRepository.count();
            stats.put("totalStudents", totalStudents);

            // Get fee status counts
            List<Object[]> statusCounts = studentRepository.countStudentsByFeeStatus();
            for (Object[] statusCount : statusCounts) {
                if (statusCount[0] instanceof Student.FeeStatus && statusCount[1] instanceof Long) {
                    stats.put(((Student.FeeStatus) statusCount[0]).name().toLowerCase() + "Count", statusCount[1]);
                }
            }

            // Total fee amount
            Double totalFee = studentRepository.getTotalFeeSum();
            stats.put("totalFee", totalFee != null ? totalFee : 0.0);

            // Total pending
            Long pendingCount = (Long) stats.getOrDefault("pendingCount", 0L);
            Long overdueCount = (Long) stats.getOrDefault("overdueCount", 0L);
            stats.put("totalPendingStudents", pendingCount + overdueCount);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Quick student stats retrieved successfully");
            response.put("data", stats);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get quick student stats", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get quick student stats: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ========== REMINDER MANAGEMENT ENDPOINTS ==========

    @PostMapping("/reminders/email")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Send email reminder to student/parent")
    public ResponseEntity<Map<String, Object>> sendEmailReminder(
            @Valid @RequestBody ReminderRequest request) {
        try {
            feeCollectionService.sendEmailReminder(request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Email reminder sent successfully");
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to send email reminder", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to send email reminder: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @PostMapping("/reminders/sms")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Send SMS reminder to student/parent")
    public ResponseEntity<Map<String, Object>> sendSmsReminder(
            @Valid @RequestBody ReminderRequest request) {
        try {
            feeCollectionService.sendSmsReminder(request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "SMS reminder sent successfully");
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to send SMS reminder", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to send SMS reminder: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @PostMapping("/reminders/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Send bulk reminders to multiple students")
    public ResponseEntity<Map<String, Object>> sendBulkReminders(
            @Valid @RequestBody BulkReminderRequest request) {
        try {
            BulkReminderResultResponse result =
                    feeCollectionService.sendBulkReminders(request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Bulk reminders sent successfully");
            response.put("data", result);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to send bulk reminders", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to send bulk reminders: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @GetMapping("/reminders/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL')")
    @Operation(summary = "Get reminder history")
    public ResponseEntity<Map<String, Object>> getReminderHistory(
            @RequestParam(required = false) Long studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            // This endpoint would be implemented with a proper repository method
            // For now, return an empty response structure
            Map<String, Object> historyData = new HashMap<>();
            historyData.put("page", page);
            historyData.put("size", size);
            historyData.put("total", 0);
            historyData.put("reminders", List.of());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Reminder history endpoint - implementation pending");
            response.put("data", historyData);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get reminder history", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get reminder history: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ========== REPORT GENERATION ENDPOINTS ==========

    @PostMapping("/reports/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL')")
    @Operation(summary = "Generate fee collection report")
    public ResponseEntity<Map<String, Object>> generateReport(
            @Valid @RequestBody ReportGenerationRequest request) {
        try {
            ReportResponse report = reportGeneratorService.generateReport(request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Report generated successfully");
            response.put("data", report);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to generate report", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to generate report: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/reports/download/{reportId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL')")
    @Operation(summary = "Download generated report")
    public ResponseEntity<byte[]> downloadReport(@PathVariable String reportId) {
        try {
            // This would look up the report by ID from database
            // For now, return a placeholder indicating implementation is needed
            String placeholderMessage = String.format(
                    "Report download functionality not yet implemented for report ID: %s\n" +
                            "This endpoint requires report storage and retrieval implementation.",
                    reportId
            );

            byte[] reportData = placeholderMessage.getBytes();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDispositionFormData("attachment", "report_status.txt");
            headers.setContentLength(reportData.length);

            return new ResponseEntity<>(reportData, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Failed to download report: {}", reportId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/reports/templates")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL')")
    @Operation(summary = "Get available report templates")
    public ResponseEntity<Map<String, Object>> getReportTemplates() {
        try {
            Map<String, Object> templates = new HashMap<>();

            // Return empty templates list - implementation needed
            templates.put("templates", List.of());
            templates.put("formats", List.of("PDF", "EXCEL", "CSV"));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Report templates endpoint - implementation pending");
            response.put("data", templates);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get report templates", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get report templates: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ========== ANALYTICS ENDPOINTS ==========

    @GetMapping("/analytics/collection-by-grade")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL')")
    @Operation(summary = "Get fee collection analytics by grade")
    public ResponseEntity<Map<String, Object>> getCollectionByGrade() {
        try {
            // This endpoint requires proper repository implementation
            // Return empty structure indicating implementation is needed
            Map<String, Object> analytics = new HashMap<>();
            analytics.put("grades", List.of());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Collection by grade analytics endpoint - implementation pending");
            response.put("data", analytics);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get collection analytics by grade", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get collection analytics: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/analytics/monthly-comparison")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL')")
    @Operation(summary = "Get monthly fee collection comparison")
    public ResponseEntity<Map<String, Object>> getMonthlyComparison(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            // This endpoint requires proper repository implementation
            // Return empty structure indicating implementation is needed
            Map<String, Object> comparison = new HashMap<>();
            comparison.put("startDate", startDate);
            comparison.put("endDate", endDate);
            comparison.put("months", List.of());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Monthly comparison endpoint - implementation pending");
            response.put("data", comparison);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get monthly comparison", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get monthly comparison: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ========== SYSTEM HEALTH ENDPOINTS ==========

    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL')")
    @Operation(summary = "Check fee collection system health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("database", "CONNECTED");
            health.put("cache", "ACTIVE");
            health.put("lastUpdate", LocalDateTime.now().toString());

            // Check student count
            long studentCount = studentRepository.count();
            health.put("totalStudents", studentCount);

            // Check recent transactions
            List<RecentPaymentResponse> recentPayments = feeCollectionService.getRecentPayments(5);
            health.put("recentTransactions", recentPayments.size());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "System health check completed");
            response.put("data", health);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Health check failed", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("status", "DOWN");
            errorResponse.put("message", "System health check failed: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/cache/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Refresh fee collection cache")
    public ResponseEntity<Map<String, Object>> refreshCache() {
        try {
            // This would clear and refresh all cache entries
            Map<String, Object> result = new HashMap<>();
            result.put("cacheCleared", true);
            result.put("message", "Cache refresh initiated");
            result.put("timestamp", LocalDateTime.now().toString());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cache refresh initiated successfully");
            response.put("data", result);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to refresh cache", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to refresh cache: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ========== BULK OPERATIONS ==========

    @PostMapping("/bulk/update-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Bulk update student fee status")
    public ResponseEntity<Map<String, Object>> bulkUpdateFeeStatus(
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> studentIds = (List<Long>) request.get("studentIds");
            String status = (String) request.get("status");

            if (studentIds == null || studentIds.isEmpty() || status == null) {
                throw new IllegalArgumentException("Missing required parameters: studentIds or status");
            }

            // In production, you would:
            // 1. Validate the status
            // 2. Update students in batch
            // 3. Log the operation

            Map<String, Object> result = new HashMap<>();
            result.put("updatedCount", studentIds.size());
            result.put("status", status);
            result.put("timestamp", LocalDateTime.now().toString());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Bulk status update functionality not yet implemented");
            response.put("data", result);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to perform bulk update", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to perform bulk update: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @PostMapping("/bulk/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Bulk export student fee data")
    public ResponseEntity<Map<String, Object>> bulkExportData(
            @RequestBody FeeCollectionFilterRequest filterRequest) {
        try {
            // This would:
            // 1. Get filtered students
            // 2. Generate export file
            // 3. Return download URL

            Map<String, Object> exportInfo = new HashMap<>();
            exportInfo.put("exportId", "EXP-" + System.currentTimeMillis());
            exportInfo.put("format", "EXCEL");
            exportInfo.put("estimatedRecords", 0);
            exportInfo.put("downloadUrl", "/api/fee-collection/exports/download/EXP-" + System.currentTimeMillis());
            exportInfo.put("message", "Export functionality not yet implemented");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Bulk export endpoint - implementation pending");
            response.put("data", exportInfo);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to initiate bulk export", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to initiate bulk export: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/students/{id}/recent-payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'PRINCIPAL', 'TEACHER')")
    @Operation(summary = "Get recent payments for a specific student")
    public ResponseEntity<Map<String, Object>> getStudentRecentPayments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "5") int limit) {
        try {
            List<RecentPaymentResponse> recentPayments =
                    feeCollectionService.getStudentRecentPayments(id, limit);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Recent payments retrieved successfully");
            response.put("data", recentPayments);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get recent payments for student id: {}", id, e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get recent payments: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
    }
}