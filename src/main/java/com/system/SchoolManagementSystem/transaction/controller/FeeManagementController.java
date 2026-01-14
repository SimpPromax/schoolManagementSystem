package com.system.SchoolManagementSystem.transaction.controller;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.transaction.service.StudentFeeUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/fees")
@RequiredArgsConstructor
@Tag(name = "Fee Management", description = "Endpoints for managing student fees")
public class FeeManagementController {

    private final StudentFeeUpdateService feeUpdateService;
    private final StudentRepository studentRepository;

    @GetMapping("/student/{studentId}/breakdown")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'TEACHER')")
    @Operation(summary = "Get detailed fee breakdown for a student")
    public ResponseEntity<?> getFeeBreakdown(@PathVariable Long studentId) {
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            StudentFeeUpdateService.FeeBreakdown breakdown = feeUpdateService.getFeeBreakdown(student);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", breakdown);
            response.put("timestamp", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get fee breakdown: " + e.getMessage());
            errorResponse.put("timestamp", java.time.LocalDateTime.now().toString());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/recalculate-all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Recalculate pending amounts for all students")
    public ResponseEntity<?> recalculateAllFees() {
        try {
            feeUpdateService.recalculateAllPendingAmounts();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Fee recalculation completed successfully");
            response.put("timestamp", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to recalculate fees: " + e.getMessage());
            errorResponse.put("timestamp", java.time.LocalDateTime.now().toString());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/student/{studentId}/manual-update")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Manually update student fee payment")
    public ResponseEntity<?> manualFeeUpdate(
            @PathVariable Long studentId,
            @RequestParam Double amount,
            @RequestParam String description) {

        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            Student updatedStudent = feeUpdateService.updateFeePayment(
                    student, amount, "MANUAL_" + System.currentTimeMillis(), description
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("Fee updated successfully. Added ₹%.2f to %s",
                    amount, student.getFullName()));
            response.put("data", Map.of(
                    "studentId", updatedStudent.getId(),
                    "totalPaid", updatedStudent.getPaidAmount(),
                    "pendingAmount", updatedStudent.getPendingAmount(),
                    "feeStatus", updatedStudent.getFeeStatus()
            ));
            response.put("timestamp", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to update fee: " + e.getMessage());
            errorResponse.put("timestamp", java.time.LocalDateTime.now().toString());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}