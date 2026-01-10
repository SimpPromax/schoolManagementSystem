package com.system.SchoolManagementSystem.transaction.util;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.transaction.entity.BankTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class TransactionMatcher {

    public Optional<Student> findMatchingStudent(BankTransaction transaction, List<Student> students) {
        String description = transaction.getDescription().toLowerCase();
        Double transactionAmount = transaction.getAmount();

        log.debug("Looking for student match in description: {}", description);

        // First, try to extract student name from description
        String extractedName = extractStudentNameFromDescription(description);
        if (extractedName != null && !extractedName.trim().isEmpty()) {
            log.debug("Extracted name from description: {}", extractedName);

            // Try exact match with extracted name
            for (Student student : students) {
                String fullName = student.getFullName().toLowerCase();
                String extractedLower = extractedName.toLowerCase();

                // Check for exact name match
                if (fullName.equals(extractedLower)) {
                    log.debug("Found exact name match: {} = {}", fullName, extractedName);
                    return Optional.of(student);
                }

                // Check if extracted name contains student name or vice versa
                if (fullName.contains(extractedLower) || extractedLower.contains(fullName)) {
                    log.debug("Found partial name match: {} contains {}", fullName, extractedName);
                    return Optional.of(student);
                }
            }
        }

        // If no match with extracted name, try matching with student names directly in description
        for (Student student : students) {
            String fullName = student.getFullName().toLowerCase();
            String[] nameParts = fullName.split(" ");

            // Check if student's full name appears in description
            if (description.contains(fullName)) {
                log.debug("Found full name in description: {}", fullName);
                return Optional.of(student);
            }

            // Check for first name match
            if (nameParts.length > 0) {
                String firstName = nameParts[0];
                if (firstName.length() > 2 && description.contains(firstName)) {
                    log.debug("Found first name in description: {}", firstName);

                    // Additional check: see if last name also appears
                    if (nameParts.length > 1) {
                        String lastName = nameParts[nameParts.length - 1];
                        if (description.contains(lastName)) {
                            log.debug("Also found last name: {}", lastName);
                            return Optional.of(student);
                        }
                    }

                    // Amount matching check for additional confidence
                    if (student.getPendingAmount() != null) {
                        double amountDiff = Math.abs(transactionAmount - student.getPendingAmount());
                        if (amountDiff <= 500) { // Within ₹500
                            log.debug("Amount match within ₹500 for student: {}", student.getFullName());
                            return Optional.of(student);
                        }
                    }
                }
            }

            // Check for common nicknames or variations
            if (hasNicknameMatch(student, description)) {
                log.debug("Found nickname match for: {}", student.getFullName());
                return Optional.of(student);
            }
        }

        log.debug("No student match found for transaction: {}", transaction.getBankReference());
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
        // Common patterns in Indian bank transaction descriptions
        String[] patterns = {
                "upi-", "neft-", "imps-", "rtgs-",
                "trf-", "pay-", "transfer-",
                "cash-", "chq-", "cheque-",
                "by ", "from ", "to ", "for "
        };

        String cleaned = description.toLowerCase();

        // Remove common prefixes
        for (String pattern : patterns) {
            if (cleaned.startsWith(pattern)) {
                cleaned = cleaned.substring(pattern.length());
                break;
            }
        }

        // Remove common suffixes
        String[] suffixes = {
                "-fee", "-school", "-payment", "-tuition",
                "-fees", "-schoolfee", "-admission", "-exam",
                "-transport", "-hostel", "-library", "-lab"
        };

        for (String suffix : suffixes) {
            if (cleaned.endsWith(suffix)) {
                cleaned = cleaned.substring(0, cleaned.length() - suffix.length());
                break;
            }
        }

        // Remove transaction references, numbers, symbols
        cleaned = cleaned.replaceAll("[0-9]", " ")
                .replaceAll("[^a-zA-Z\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // Extract what looks like a name (capitalized words or common Indian names)
        String[] words = cleaned.split(" ");
        StringBuilder nameBuilder = new StringBuilder();

        for (String word : words) {
            if (word.length() > 2 && !isCommonWord(word)) {
                if (nameBuilder.length() > 0) {
                    nameBuilder.append(" ");
                }
                // Capitalize first letter of each name part
                nameBuilder.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase());
            }
        }

        String extracted = nameBuilder.toString().trim();
        log.debug("Extracted name from '{}': '{}'", description, extracted);
        return extracted.isEmpty() ? null : extracted;
    }

    public Double calculateMatchScore(BankTransaction transaction, Student student, Double pendingFee) {
        double score = 0.0;
        String description = transaction.getDescription().toLowerCase();
        String studentName = student.getFullName().toLowerCase();

        log.debug("Calculating match score for {} - {}", studentName, transaction.getBankReference());

        // Exact name match (40 points)
        if (description.contains(studentName)) {
            score += 40;
            log.debug("  +40: Exact name match");
        }

        // Name extraction match (35 points)
        String extractedName = extractStudentNameFromDescription(description);
        if (extractedName != null && extractedName.toLowerCase().equals(studentName)) {
            score += 35;
            log.debug("  +35: Extracted name matches");
        }

        // Partial name match (20 points)
        String[] nameParts = studentName.split(" ");
        boolean hasPartialMatch = false;
        for (String part : nameParts) {
            if (part.length() > 3 && description.contains(part)) {
                score += 20;
                hasPartialMatch = true;
                log.debug("  +20: Partial name match for '{}'", part);
                break;
            }
        }

        // Amount match with pending fee (30 points)
        if (pendingFee != null) {
            double amountDiff = Math.abs(transaction.getAmount() - pendingFee);
            if (amountDiff == 0) {
                score += 30;
                log.debug("  +30: Exact amount match");
            } else if (amountDiff <= 100) {
                score += 20;
                log.debug("  +20: Amount within ₹100");
            } else if (amountDiff <= 500) {
                score += 10;
                log.debug("  +10: Amount within ₹500");
            }
        }

        // Date relevance (recent transactions get higher score - 10 points)
        long daysOld = java.time.temporal.ChronoUnit.DAYS.between(
                transaction.getTransactionDate(), LocalDate.now());
        if (daysOld <= 7) {
            score += 10;
            log.debug("  +10: Recent transaction ({} days old)", daysOld);
        } else if (daysOld <= 30) {
            score += 5;
            log.debug("  +5: Transaction within 30 days");
        }

        // Keyword matches (10 points)
        if (description.contains("fee") || description.contains("school") ||
                description.contains("tuition") || description.contains("payment") ||
                description.contains("springfield")) {
            score += 10;
            log.debug("  +10: Contains fee/school keywords");
        }

        // Penalty for common false positives
        if (description.contains("atm") || description.contains("cash withdrawal") ||
                description.contains("self transfer")) {
            score -= 20;
            log.debug("  -20: Possible false positive (ATM/withdrawal)");
        }

        log.debug("  Total score: {}", score);
        return score;
    }

    // Helper methods
    private boolean isCommonWord(String word) {
        String[] commonWords = {
                "bank", "transfer", "payment", "fee", "fees", "school",
                "tuition", "college", "institute", "academy", "education",
                "to", "by", "from", "for", "the", "and", "via", "through"
        };

        for (String common : commonWords) {
            if (word.equalsIgnoreCase(common)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNicknameMatch(Student student, String description) {
        // Common Indian nickname mappings
        String fullName = student.getFullName().toLowerCase();

        // Check for common nicknames
        if (fullName.contains("arjun") && description.contains("arun")) return true;
        if (fullName.contains("arun") && description.contains("arjun")) return true;
        if (fullName.contains("vivek") && description.contains("viv")) return true;
        if (fullName.contains("vikram") && description.contains("vik")) return true;
        if (fullName.contains("priya") && description.contains("pri")) return true;
        if (fullName.contains("anjali") && description.contains("anju")) return true;
        if (fullName.contains("rohan") && description.contains("ro")) return true;
        if (fullName.contains("ishita") && description.contains("ish")) return true;

        return false;
    }
}