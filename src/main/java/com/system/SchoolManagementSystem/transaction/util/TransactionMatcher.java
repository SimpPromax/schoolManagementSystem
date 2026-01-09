package com.system.SchoolManagementSystem.transaction.util;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.transaction.entity.BankTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class TransactionMatcher {

    public Optional<Student> findMatchingStudent(BankTransaction transaction, List<Student> students) {
        String description = transaction.getDescription().toLowerCase();

        for (Student student : students) {
            String fullName = student.getFullName().toLowerCase();
            String[] nameParts = fullName.split(" ");

            // Check if student name appears in description
            if (description.contains(fullName)) {
                return Optional.of(student);
            }

            // Check for first name match
            if (nameParts.length > 0 && description.contains(nameParts[0])) {
                return Optional.of(student);
            }

            // Check for last name match
            if (nameParts.length > 1 && description.contains(nameParts[nameParts.length - 1])) {
                return Optional.of(student);
            }

            // Check for partial matches (useful for typos)
            for (String namePart : nameParts) {
                if (namePart.length() > 3 && description.contains(namePart.substring(0, Math.min(4, namePart.length())))) {
                    return Optional.of(student);
                }
            }
        }

        return Optional.empty();
    }

    public boolean isAmountMatch(Double transactionAmount, Double expectedAmount, Double tolerancePercentage) {
        if (expectedAmount == null) return false;

        double tolerance = expectedAmount * (tolerancePercentage / 100);
        double minAmount = expectedAmount - tolerance;
        double maxAmount = expectedAmount + tolerance;

        return transactionAmount >= minAmount && transactionAmount <= maxAmount;
    }

    public String extractStudentNameFromDescription(String description) {
        // Simple heuristic to extract names from bank descriptions
        String[] commonPrefixes = {"UPI-", "NEFT-", "IMPS-", "TRF-", "PAY-", "TRANSFER-", "CASH-", "CHQ-"};
        String[] commonSuffixes = {"-FEE", "-SCHOOL", "-PAYMENT", "-TUITION"};

        String cleaned = description;

        // Remove common prefixes
        for (String prefix : commonPrefixes) {
            if (cleaned.startsWith(prefix)) {
                cleaned = cleaned.substring(prefix.length());
                break;
            }
        }

        // Remove common suffixes
        for (String suffix : commonSuffixes) {
            if (cleaned.endsWith(suffix)) {
                cleaned = cleaned.substring(0, cleaned.length() - suffix.length());
                break;
            }
        }

        // Extract what looks like a name (words starting with capital letters)
        String[] words = cleaned.split("[\\s\\-]+");
        StringBuilder nameBuilder = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0 && Character.isUpperCase(word.charAt(0))) {
                if (nameBuilder.length() > 0) {
                    nameBuilder.append(" ");
                }
                nameBuilder.append(word);
            }
        }

        return nameBuilder.toString().trim();
    }

    public Double calculateMatchScore(BankTransaction transaction, Student student, Double pendingFee) {
        double score = 0.0;
        String description = transaction.getDescription().toLowerCase();
        String studentName = student.getFullName().toLowerCase();

        // Exact name match
        if (description.contains(studentName)) {
            score += 40;
        }

        // Partial name match
        String[] nameParts = studentName.split(" ");
        for (String part : nameParts) {
            if (part.length() > 3 && description.contains(part)) {
                score += 20;
                break;
            }
        }

        // Amount match with pending fee
        if (pendingFee != null && Math.abs(transaction.getAmount() - pendingFee) <= 100) {
            score += 30;
        }

        // Date relevance (recent transactions get higher score)
        long daysOld = java.time.temporal.ChronoUnit.DAYS.between(
                transaction.getTransactionDate(), java.time.LocalDate.now());
        if (daysOld <= 7) {
            score += 10;
        }

        // Keyword matches
        if (description.contains("fee") || description.contains("school") ||
                description.contains("tuition") || description.contains("payment")) {
            score += 10;
        }

        return score;
    }
}