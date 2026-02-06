package com.system.SchoolManagementSystem.transaction.util;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.termmanagement.repository.StudentTermAssignmentRepository;
import com.system.SchoolManagementSystem.transaction.validation.TransactionValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StudentFeeValidator implements CommandLineRunner {

    private final StudentRepository studentRepository;
    private final StudentTermAssignmentRepository studentTermAssignmentRepository;
    private final TransactionValidationService validationService;

    @Override
    public void run(String... args) throws Exception {
        if (args.length > 0 && args[0].equals("validate-students")) {
            log.info("🔍 Starting student fee validation...");

            List<Student> activeStudents = studentRepository.findByStatus(Student.StudentStatus.ACTIVE);

            log.info("📊 Found {} active students", activeStudents.size());

            int validCount = 0;
            int invalidCount = 0;

            for (Student student : activeStudents) {
                TransactionValidationService.ValidationResult result =
                        validationService.validateStudentForPayment(
                                student.getId(), student.getFullName()
                        );

                if (result.isValid()) {
                    validCount++;
                    log.info("✅ Student {}: {} assignments, ₹{} pending",
                            student.getFullName(),
                            result.getTermAssignmentCount(),
                            result.getTotalPendingAmount());
                } else {
                    invalidCount++;
                    log.error("❌ Student {}: {}",
                            student.getFullName(), result.getMessage());
                }
            }

            log.info("📈 Validation Complete: {} valid, {} invalid", validCount, invalidCount);

            if (invalidCount > 0) {
                log.warn("⚠️ {} students need term assignments/fees before accepting payments", invalidCount);
            }
        }
    }
}