package com.system.SchoolManagementSystem.fee.service;

import com.system.SchoolManagementSystem.fee.entity.FeeReminder;
import com.system.SchoolManagementSystem.student.entity.Student;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class EmailService {

    @Async
    public boolean sendFeeReminderEmail(Student student, FeeReminder reminder) {
        try {
            // In a real implementation, integrate with email service (SMTP, SendGrid, etc.)
            log.info("Sending email reminder to: {} ({})",
                    student.getEmail(), student.getFullName());

            // Simulate email sending
            Thread.sleep(1000);

            log.info("Email sent successfully to: {}", student.getEmail());
            return true;

        } catch (Exception e) {
            log.error("Failed to send email to: {}", student.getEmail(), e);
            return false;
        }
    }

    public Map<String, String> getEmailTemplates() {
        Map<String, String> templates = new HashMap<>();

        templates.put("GENTLE", """
            Dear {parentName},
            
            This is a gentle reminder that the school fee for {studentName} ({grade}) is pending.
            
            Payment Details:
            • Total Fee: ₹{totalFee}
            • Amount Paid: ₹{paidAmount}
            • Amount Due: ₹{pendingAmount}
            • Due Date: {dueDate}
            
            Please complete the payment at your earliest convenience.
            
            Best regards,
            School Accounts Department
            """);

        templates.put("OVERDUE", """
            URGENT: School Fee Overdue Notice
            
            Dear {parentName},
            
            This is an overdue notice for school fee payment for {studentName} ({grade}).
            
            Payment Details:
            • Total Fee: ₹{totalFee}
            • Amount Paid: ₹{paidAmount}
            • Overdue Amount: ₹{pendingAmount}
            • Original Due Date: {dueDate}
            
            Please make the payment immediately to avoid any late fees or restrictions.
            
            Best regards,
            School Accounts Department
            """);

        templates.put("FINAL", """
            FINAL NOTICE: School Fee Payment
            
            Dear {parentName},
            
            This is the final notice regarding the school fee payment for {studentName} ({grade}).
            
            Payment Details:
            • Total Fee: ₹{totalFee}
            • Amount Paid: ₹{paidAmount}
            • Outstanding Amount: ₹{pendingAmount}
            • Due Date: {dueDate} (Already Passed)
            
            Failure to pay within 48 hours may result in temporary suspension from classes.
            
            Best regards,
            School Accounts Department
            """);

        return templates;
    }
}