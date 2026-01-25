package com.system.SchoolManagementSystem.transaction.util;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.transaction.entity.BankTransaction;
import com.system.SchoolManagementSystem.transaction.service.StudentCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionMatcher {

    // ========== DEPENDENCIES ==========
    private final StudentCacheService studentCacheService;

    // ========== PATTERNS ==========
    private static final Pattern STUDENT_ID_PATTERN =
            Pattern.compile("\\b(STU|STD|S/|ID:?)\\s*\\d{4,}\\w*\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern NAME_PATTERN =
            Pattern.compile("\\b([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+)\\b");

    // Common Kenyan school fee amounts (in KES)
    private static final double[] COMMON_KENYAN_SCHOOL_AMOUNTS = {
            500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000,
            5500, 6000, 6500, 7000, 7500, 8000, 8500, 9000, 9500, 10000,
            11000, 12000, 13000, 14000, 15000, 20000, 25000, 30000, 35000, 40000, 50000,
            60000, 70000, 80000, 90000, 100000
    };

    // ========== PUBLIC METHODS ==========

    /**
     * Main matching method - uses StudentCacheService
     */
    public Optional<Student> findMatchingStudent(BankTransaction transaction) {
        if (transaction == null || transaction.getDescription() == null) {
            return Optional.empty();
        }

        String description = transaction.getDescription().toLowerCase().trim();
        Double amount = transaction.getAmount();

        log.debug("🔍 Matcher searching: '{}' (KES {})", description, amount);

        // ========== CHECK CACHE ==========
        if (!studentCacheService.isCacheLoaded()) {
            log.warn("⚠️ StudentCacheService not loaded! Cannot match.");
            return Optional.empty();
        }

        // ========== STRATEGY 1: EXACT NAME MATCH ==========
        Set<String> allStudentNames = studentCacheService.getAllNames();

        for (String studentName : allStudentNames) {
            if (description.contains(studentName)) {
                Optional<Student> matchedStudent = studentCacheService.findByName(studentName);
                if (matchedStudent.isPresent()) {
                    log.info("🎯 Exact name match: '{}' → {}", studentName, matchedStudent.get().getFullName());
                    return matchedStudent;
                }
            }
        }

        // ========== STRATEGY 2: NAME PARTS MATCH ==========
        // Extract words from description that could be names
        String[] words = description.split("[^a-z]+");
        for (String word : words) {
            if (word.length() >= 3) { // Only consider words with 3+ characters
                List<Student> candidates = studentCacheService.findByNamePart(word);
                if (!candidates.isEmpty()) {
                    // Check if full name appears
                    for (Student candidate : candidates) {
                        String fullName = candidate.getFullName().toLowerCase();
                        if (description.contains(fullName)) {
                            log.info("🎯 Name part match: '{}' → {}", word, candidate.getFullName());
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }

        // ========== STRATEGY 3: STUDENT ID MATCH ==========
        Matcher studentIdMatcher = STUDENT_ID_PATTERN.matcher(description);
        if (studentIdMatcher.find()) {
            String possibleId = studentIdMatcher.group().replaceAll("(?i)(stu|std|s/|id:?)\\s*", "").trim();
            // Search through all names to find student with this ID
            for (String studentName : allStudentNames) {
                Optional<Student> student = studentCacheService.findByName(studentName);
                if (student.isPresent() && student.get().getStudentId() != null) {
                    if (student.get().getStudentId().toLowerCase().contains(possibleId.toLowerCase())) {
                        log.info("🎯 Student ID match: '{}' → {}", possibleId, student.get().getFullName());
                        return student;
                    }
                }
            }
        }

        // ========== STRATEGY 4: AMOUNT MATCH ==========
        if (amount != null && amount > 0) {
            List<Student> amountCandidates = studentCacheService.findByAmount(amount);
            if (!amountCandidates.isEmpty()) {
                // Filter by name in description
                for (Student candidate : amountCandidates) {
                    String studentName = candidate.getFullName().toLowerCase();
                    if (description.contains(studentName)) {
                        log.info("🎯 Amount + name match: KES {} → {}", amount, candidate.getFullName());
                        return Optional.of(candidate);
                    }
                }
            }
        }

        log.debug("❌ No match found for: '{}'", description);
        return Optional.empty();
    }

    /**
     * Enhanced matching with description cleaning
     */
    public Optional<Student> enhancedFindMatchingStudent(BankTransaction transaction) {
        if (transaction == null || transaction.getDescription() == null) {
            return Optional.empty();
        }

        String description = transaction.getDescription().toLowerCase();
        log.debug("🔍 Enhanced matching for: '{}'", description);

        // Clean description
        String cleanedDesc = cleanDescriptionForMatching(description);

        // Check cache
        if (!studentCacheService.isCacheLoaded()) {
            return Optional.empty();
        }

        // Try exact match first
        Optional<Student> exactMatch = findMatchingStudent(transaction);
        if (exactMatch.isPresent()) {
            return exactMatch;
        }

        // Try with cleaned description
        Set<String> allNames = studentCacheService.getAllNames();

        for (String studentName : allNames) {
            if (cleanedDesc.contains(studentName)) {
                Optional<Student> student = studentCacheService.findByName(studentName);
                if (student.isPresent()) {
                    log.info("🎯 Enhanced match: '{}' → {}", studentName, student.get().getFullName());
                    return student;
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Calculate match score for ranking
     */
    public Double calculateMatchScore(BankTransaction transaction, Student student, Double pendingFee) {
        double score = 0.0;
        String description = transaction.getDescription().toLowerCase();
        String studentName = student.getFullName().toLowerCase();
        String[] studentNameParts = studentName.split("\\s+");
        Double transactionAmount = transaction.getAmount();

        // ========== NAME MATCHING (MAX 40 POINTS) ==========
        if (description.contains(studentName)) {
            score += 25; // Exact name match
        }

        // Name parts
        if (studentNameParts.length >= 2) {
            String firstName = studentNameParts[0];
            String lastName = studentNameParts[studentNameParts.length - 1];
            if (description.contains(firstName) && description.contains(lastName)) {
                score += 15;
            }
        }

        // Student ID match (high priority)
        if (student.getStudentId() != null) {
            String studentId = student.getStudentId().toLowerCase();
            if (description.contains(studentId)) {
                score += 30;
            }
        }

        // ========== AMOUNT MATCHING (MAX 30 POINTS) ==========
        if (pendingFee != null && pendingFee > 0) {
            if (Math.abs(transactionAmount - pendingFee) < 0.01) {
                score += 25;
            } else if (isCommonKenyanSchoolAmount(transactionAmount)) {
                score += 20;
            }
        } else if (isCommonKenyanSchoolAmount(transactionAmount)) {
            score += 15;
        }

        // ========== CONTEXT MATCHING (MAX 20 POINTS) ==========
        long daysOld = ChronoUnit.DAYS.between(transaction.getTransactionDate(), LocalDate.now());
        if (daysOld <= 7) {
            score += 10;
        }

        if (hasSchoolKeywords(description)) {
            score += 5;
        }

        // ========== PENALTIES ==========
        if (isLikelyFalsePositive(description)) {
            score -= 30;
        }

        // Ensure score is between 0-100
        score = Math.max(0, Math.min(score, 100));

        return score;
    }

    // ========== HELPER METHODS ==========

    private String cleanDescriptionForMatching(String description) {
        // Remove common transaction prefixes/suffixes
        String cleaned = description
                .replaceAll("(?i)(upi|neft|rtgs|imps|cash|cheque|card)[-_\\s]*", " ")
                .replaceAll("(?i)(tuition|fee|fees|payment|school|admission)[-_\\s]*", " ")
                .replaceAll("[^a-z\\s]", " ")  // Remove special characters
                .replaceAll("\\s+", " ")      // Normalize spaces
                .trim();

        return cleaned;
    }

    private boolean isCommonKenyanSchoolAmount(Double amount) {
        if (amount == null) return false;

        for (double common : COMMON_KENYAN_SCHOOL_AMOUNTS) {
            if (Math.abs(amount - common) < 0.01) {
                return true;
            }
        }

        return amount % 100 == 0 || amount % 500 == 0 || amount % 1000 == 0;
    }

    private boolean hasSchoolKeywords(String description) {
        String[] keywords = {
                "school", "tuition", "fee", "fees", "payment", "college",
                "education", "academy", "institute", "admission", "exam"
        };

        for (String keyword : keywords) {
            if (description.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLikelyFalsePositive(String description) {
        String[] falsePositives = {
                "mpesa", "airtime", "safaricom", "equitel", "tkash", "kcb",
                "cooperative", "sacco", "loan", "chama", "contribution",
                "shopping", "naivas", "tuskys", "nakumatt", "carrefour",
                "electricity", "kplc", "water", "rent", "fuel", "petrol"
        };

        for (String fp : falsePositives) {
            if (description.contains(fp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if cache is loaded
     */
    public boolean isCacheLoaded() {
        return studentCacheService.isCacheLoaded();
    }

    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStats() {
        StudentCacheService.CacheStats stats = StudentCacheService.CacheStats.fromService(studentCacheService);

        Map<String, Object> result = new HashMap<>();
        result.put("studentCount", stats.getStudentCount());
        result.put("exactNameEntries", stats.getExactNameEntries());
        result.put("namePartEntries", stats.getNamePartEntries());
        result.put("amountEntries", stats.getAmountEntries());
        result.put("isLoaded", stats.isLoaded());

        return result;
    }

    /**
     * Clear cache (delegates to StudentCacheService)
     */
    public void clearCache() {
        studentCacheService.refreshCache();
        log.info("🔄 Student cache refresh initiated");
    }

    /**
     * Initialize cache (delegates to StudentCacheService)
     */
    public void initializeCache(List<Student> students) {
        // StudentCacheService loads automatically via @PostConstruct
        // This method is kept for compatibility
        log.info("TransactionMatcher: StudentCacheService loads automatically");
    }
}