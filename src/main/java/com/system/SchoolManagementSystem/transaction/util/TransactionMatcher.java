package com.system.SchoolManagementSystem.transaction.util;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.transaction.entity.BankTransaction;
import com.system.SchoolManagementSystem.transaction.enums.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class TransactionMatcher {

    // Common school fee amounts in India (in rupees)
    private static final double[] COMMON_SCHOOL_AMOUNTS = {
            500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000,
            5500, 6000, 6500, 7000, 7500, 8000, 8500, 9000, 9500, 10000,
            11000, 12000, 13000, 14000, 15000, 20000, 25000, 30000, 35000, 40000, 50000
    };

    // School-related keywords
    private static final String[] SCHOOL_KEYWORDS = {
            "school", "tuition", "fee", "fees", "payment", "college",
            "education", "academy", "institute", "springfield", "admission",
            "exam", "test", "term", "quarter", "semester", "annual",
            "hostel", "bus", "transport", "uniform", "book", "stationery",
            "library", "lab", "sports", "activity", "workshop", "trip",
            "medical", "development", "maintenance", "computer", "science"
    };

    // False positive indicators (likely NOT school payments)
    private static final String[] FALSE_POSITIVES = {
            "atm", "withdrawal", "cash withdrawal", "self transfer",
            "salary", "loan", "emi", "investment", "insurance",
            "shopping", "amazon", "flipkart", "swiggy", "zomato",
            "movie", "cinema", "petrol", "fuel", "diesel",
            "restaurant", "hotel", "flight", "travel", "holiday",
            "shopping", "mall", "market", "grocery", "vegetable",
            "mobile", "recharge", "dth", "electricity", "water",
            "gas", "lpg", "rent", "emi", "credit card", "bill"
    };

    // Additional fee indicators
    private static final String[] ADDITIONAL_FEE_INDICATORS = {
            "exam", "test", "transport", "bus", "uniform", "dress",
            "book", "stationery", "library", "lab", "sports", "game",
            "activity", "workshop", "trip", "tour", "medical", "health",
            "admission", "registration", "enrollment", "development",
            "maintenance", "computer", "science", "art", "music", "dance"
    };

    public Optional<Student> findMatchingStudent(BankTransaction transaction, List<Student> students) {
        String description = transaction.getDescription().toLowerCase();
        Double transactionAmount = transaction.getAmount();

        log.debug("🔍 Looking for student match in description: {}", description);

        // First, try to extract student name from description
        String extractedName = extractStudentNameFromDescription(description);

        if (extractedName != null && !extractedName.trim().isEmpty()) {
            log.debug("Extracted name from description: {}", extractedName);
            String extractedLower = extractedName.toLowerCase();

            // Try exact match with extracted name
            for (Student student : students) {
                String fullName = student.getFullName().toLowerCase();

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

            // Check for first name match with additional confidence checks
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
                    if (hasReasonableSchoolAmount(transactionAmount)) {
                        log.debug("Amount ₹{} is reasonable for school payment", transactionAmount);
                        return Optional.of(student);
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

    public Double calculateMatchScore(BankTransaction transaction, Student student, Double pendingFee) {
        double score = 0.0;
        String description = transaction.getDescription().toLowerCase();
        String studentName = student.getFullName().toLowerCase();
        String[] studentNameParts = studentName.split(" ");
        Double transactionAmount = transaction.getAmount();

        log.debug("📊 Calculating match score for {} - Transaction: ₹{} ('{}')",
                student.getFullName(), transactionAmount, transaction.getDescription());

        // ========== CATEGORY 1: NAME MATCHING (MAX 40 POINTS) ==========

        // 1A. Exact full name match in description (25 points)
        if (description.contains(studentName)) {
            score += 25;
            log.debug("  ✅ +25: Exact full name '{}' found in description", student.getFullName());
        }

        // 1B. Name extraction match (20 points)
        String extractedName = extractStudentNameFromDescription(description);
        if (extractedName != null) {
            String extractedLower = extractedName.toLowerCase();
            if (extractedLower.equals(studentName)) {
                score += 20;
                log.debug("  ✅ +20: Extracted name '{}' matches exactly", extractedName);
            } else if (extractedLower.contains(studentName) || studentName.contains(extractedLower)) {
                score += 15;
                log.debug("  ✅ +15: Extracted name '{}' partially matches", extractedName);
            }
        }

        // 1C. First + Last name combo (15 points)
        if (studentNameParts.length >= 2) {
            String firstName = studentNameParts[0];
            String lastName = studentNameParts[studentNameParts.length - 1];
            if (description.contains(firstName) && description.contains(lastName)) {
                score += 15;
                log.debug("  ✅ +15: Both first name '{}' and last name '{}' found", firstName, lastName);
            }
        }

        // 1D. First name only (5 points)
        if (studentNameParts.length > 0) {
            String firstName = studentNameParts[0];
            if (firstName.length() >= 3 && description.contains(firstName)) {
                score += 5;
                log.debug("  ✅ +5: First name '{}' found", firstName);
            }
        }

        // ========== CATEGORY 2: AMOUNT MATCHING (MAX 40 POINTS) ==========

        if (pendingFee != null) {
            // Student has fee information
            if (pendingFee > 0) {
                // Student has pending fees

                // 2A. Perfect amount match with pending (30 points)
                if (Math.abs(transactionAmount - pendingFee) < 0.01) {
                    score += 30;
                    log.debug("  ✅ +30: Perfect amount match! ₹{} = Pending ₹{}", transactionAmount, pendingFee);
                }
                // 2B. Common school payment amounts (25 points)
                else if (isCommonSchoolAmount(transactionAmount)) {
                    score += 25;
                    log.debug("  ✅ +25: Amount ₹{} is a common school payment amount", transactionAmount);
                }
                // 2C. Partial payment (20 points)
                else if (transactionAmount < pendingFee && transactionAmount > 0) {
                    score += 20;
                    log.debug("  ✅ +20: Partial payment of ₹{} towards pending ₹{}", transactionAmount, pendingFee);
                }
                // 2D. Close amount match (15 points)
                else if (Math.abs(transactionAmount - pendingFee) <= 500) {
                    score += 15;
                    log.debug("  ✅ +15: Amount ₹{} is close to pending ₹{} (within ₹500)", transactionAmount, pendingFee);
                }
                // 2E. Reasonable amount range (10 points)
                else if (transactionAmount >= 500 && transactionAmount <= 50000) {
                    score += 10;
                    log.debug("  ✅ +10: Amount ₹{} in reasonable school range", transactionAmount);
                }
            } else {
                // Student has NO pending fees (already paid)
                // This could be additional fees (exam, transport, uniform, etc.)

                // 2F. Additional school fees (25 points)
                if (isAdditionalFeeTransaction(description, transactionAmount)) {
                    score += 25;
                    log.debug("  ✅ +25: Amount ₹{} looks like additional school fee", transactionAmount);
                }
                // 2G. Common school amount (20 points)
                else if (isCommonSchoolAmount(transactionAmount)) {
                    score += 20;
                    log.debug("  ✅ +20: Amount ₹{} is common school amount", transactionAmount);
                }
                // 2H. Reasonable school payment amount (15 points)
                else if (transactionAmount >= 500 && transactionAmount <= 50000) {
                    score += 15;
                    log.debug("  ✅ +15: Amount ₹{} is reasonable for school payment", transactionAmount);
                }
            }
        } else {
            // No fee info available
            // 2I. Common school amount (15 points)
            if (isCommonSchoolAmount(transactionAmount)) {
                score += 15;
                log.debug("  ✅ +15: Amount ₹{} is common school amount", transactionAmount);
            }
            // 2J. Reasonable amount range (10 points)
            else if (transactionAmount >= 500 && transactionAmount <= 50000) {
                score += 10;
                log.debug("  ✅ +10: Amount ₹{} in reasonable school range", transactionAmount);
            }
        }

        // ========== CATEGORY 3: TRANSACTION CONTEXT (MAX 20 POINTS) ==========

        // 3A. School-related keywords (10 points)
        if (hasSchoolKeywords(description)) {
            score += 10;
            log.debug("  ✅ +10: Contains school-related keywords");
        }

        // 3B. Payment method confidence (5 points)
        if (isReliablePaymentMethod(transaction.getPaymentMethod())) {
            score += 5;
            log.debug("  ✅ +5: Reliable payment method: {}", transaction.getPaymentMethod());
        }

        // 3C. Recent transaction (5 points)
        long daysOld = java.time.temporal.ChronoUnit.DAYS.between(
                transaction.getTransactionDate(), LocalDate.now());
        if (daysOld <= 30) {
            score += 5;
            log.debug("  ✅ +5: Recent transaction ({} days old)", daysOld);
        } else if (daysOld <= 90) {
            score += 3;
            log.debug("  ✅ +3: Somewhat recent transaction ({} days old)", daysOld);
        }

        // ========== CATEGORY 4: PENALTIES (DEDUCT POINTS) ==========

        // 4A. False positive indicators (severe penalty)
        if (isLikelyFalsePositive(description)) {
            score -= 40;
            log.debug("  ⚠️ -40: Possible false positive transaction");
        }

        // 4B. Unusual payment methods for schools
        if (isUnusualForSchool(transaction.getPaymentMethod())) {
            score -= 20;
            log.debug("  ⚠️ -20: Unusual payment method for school: {}", transaction.getPaymentMethod());
        }

        // 4C. Very old transaction
        if (daysOld > 180) {
            score -= 15;
            log.debug("  ⚠️ -15: Very old transaction ({} days)", daysOld);
        }

        // 4D. Unusual amount for school
        if (transactionAmount < 100 || transactionAmount > 100000) {
            score -= 15;
            log.debug("  ⚠️ -15: Unusual amount for school payment: ₹{}", transactionAmount);
        }

        // 4E. Generic description (small penalty)
        if (hasGenericDescription(description)) {
            score -= 5;
            log.debug("  ⚠️ -5: Generic transaction description");
        }

        // Ensure score doesn't go negative
        score = Math.max(0, score);

        // Cap at 100
        score = Math.min(score, 100);

        log.debug("  📈 FINAL SCORE: {}/100", String.format("%.1f", score));
        return score;
    }

    // ========== HELPER METHODS ==========

    public String extractStudentNameFromDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return null;
        }

        // Common patterns in Indian bank transaction descriptions
        String[] patterns = {
                "upi-", "neft-", "imps-", "rtgs-",
                "trf-", "pay-", "transfer-", "payment-",
                "cash-", "chq-", "cheque-", "dd-",
                "by ", "from ", "to ", "for ", "via "
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
                "-fee", "-fees", "-school", "-payment", "-tuition",
                "-admission", "-exam", "-test", "-transport", "-hostel",
                "-library", "-lab", "-uniform", "-book", "-stationery"
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
            if (word.length() > 2 && !isCommonWord(word) && !isSchoolKeyword(word)) {
                if (nameBuilder.length() > 0) {
                    nameBuilder.append(" ");
                }
                // Capitalize first letter of each name part
                nameBuilder.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase());
            }
        }

        String extracted = nameBuilder.toString().trim();

        // If extracted name looks like initials only (e.g., "A B"), discard
        if (extracted.length() <= 3 && extracted.contains(" ")) {
            return null;
        }

        log.debug("Extracted name from '{}': '{}'", description, extracted);
        return extracted.isEmpty() ? null : extracted;
    }

    private boolean isCommonSchoolAmount(Double amount) {
        if (amount == null) return false;

        for (double common : COMMON_SCHOOL_AMOUNTS) {
            if (Math.abs(amount - common) < 0.01) {
                return true;
            }
        }
        return false;
    }

    private boolean isAdditionalFeeTransaction(String description, Double amount) {
        if (description == null || amount == null) return false;

        String descLower = description.toLowerCase();

        // Check for additional fee indicators
        boolean isAdditionalFee = false;
        for (String indicator : ADDITIONAL_FEE_INDICATORS) {
            if (descLower.contains(indicator)) {
                isAdditionalFee = true;
                break;
            }
        }

        // Additional fees are usually smaller (typically ₹500 - ₹10,000)
        boolean isReasonableAmount = amount >= 500 && amount <= 10000;

        return isAdditionalFee && isReasonableAmount;
    }

    private boolean hasSchoolKeywords(String description) {
        if (description == null) return false;

        String descLower = description.toLowerCase();

        for (String keyword : SCHOOL_KEYWORDS) {
            if (descLower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isReliablePaymentMethod(PaymentMethod method) {
        if (method == null) return false;

        // More reliable payment methods for schools
        switch (method) {
            case UPI:
            case NEFT:
            case RTGS:
            case IMPS:
            case CHEQUE:
            case BANK_TRANSFER:
            case ONLINE_BANKING:
                return true;
            default:
                return false;
        }
    }

    private boolean isUnusualForSchool(PaymentMethod method) {
        if (method == null) return false;

        // Less common for school payments
        switch (method) {
            case CASH: // Cash is less traceable
                return true;
            default:
                return false;
        }
    }

    private boolean isLikelyFalsePositive(String description) {
        if (description == null) return false;

        String descLower = description.toLowerCase();

        for (String fp : FALSE_POSITIVES) {
            if (descLower.contains(fp)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasGenericDescription(String description) {
        if (description == null) return false;

        String descLower = description.toLowerCase();
        String[] genericTerms = {
                "payment", "transfer", "transaction", "fund", "amount",
                "credit", "debit", "received", "paid", "sent"
        };

        // Check if description is mostly generic terms
        int genericCount = 0;
        for (String term : genericTerms) {
            if (descLower.contains(term)) {
                genericCount++;
            }
        }

        return genericCount >= 2 && descLower.split(" ").length <= 4;
    }

    private boolean hasReasonableSchoolAmount(Double amount) {
        if (amount == null) return false;
        return amount >= 500 && amount <= 50000;
    }

    private boolean isCommonWord(String word) {
        String[] commonWords = {
                "bank", "transfer", "payment", "fee", "fees", "school",
                "tuition", "college", "institute", "academy", "education",
                "to", "by", "from", "for", "the", "and", "via", "through",
                "received", "paid", "sent", "amount", "fund", "transaction",
                "credit", "debit", "account", "customer", "client", "user"
        };

        String wordLower = word.toLowerCase();
        for (String common : commonWords) {
            if (wordLower.equals(common)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSchoolKeyword(String word) {
        String wordLower = word.toLowerCase();
        for (String keyword : SCHOOL_KEYWORDS) {
            if (wordLower.equals(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNicknameMatch(Student student, String description) {
        // Common Indian nickname mappings
        String fullName = student.getFullName().toLowerCase();
        String descLower = description.toLowerCase();

        // Check for common nicknames
        if (fullName.contains("arjun") && descLower.contains("arun")) return true;
        if (fullName.contains("arun") && descLower.contains("arjun")) return true;
        if (fullName.contains("vivek") && (descLower.contains("viv") || descLower.contains("vive"))) return true;
        if (fullName.contains("vikram") && (descLower.contains("vik") || descLower.contains("vikky"))) return true;
        if (fullName.contains("priya") && (descLower.contains("pri") || descLower.contains("piu"))) return true;
        if (fullName.contains("anjali") && (descLower.contains("anju") || descLower.contains("anj"))) return true;
        if (fullName.contains("rohan") && (descLower.contains("ro") || descLower.contains("roh"))) return true;
        if (fullName.contains("ishita") && (descLower.contains("ish") || descLower.contains("ishu"))) return true;
        if (fullName.contains("sneha") && descLower.contains("snu")) return true;
        if (fullName.contains("kabir") && descLower.contains("kab")) return true;

        return false;
    }

    public boolean isAmountMatch(Double transactionAmount, Double expectedAmount, Double tolerancePercentage) {
        if (expectedAmount == null) return false;

        double tolerance = expectedAmount * (tolerancePercentage / 100);
        double minAmount = expectedAmount - tolerance;
        double maxAmount = expectedAmount + tolerance;

        return transactionAmount >= minAmount && transactionAmount <= maxAmount;
    }
}