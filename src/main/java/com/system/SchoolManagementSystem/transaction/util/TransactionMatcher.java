package com.system.SchoolManagementSystem.transaction.util;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.transaction.entity.BankTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class TransactionMatcher {

    // ========== CACHE INDICES ==========
    private final Map<String, Student> exactNameCache = new ConcurrentHashMap<>();
    private final Map<String, List<Student>> namePartIndex = new ConcurrentHashMap<>();
    private final Map<String, List<Student>> amountIndex = new ConcurrentHashMap<>();
    private final AtomicBoolean cacheLoaded = new AtomicBoolean(false);

    // Common school fee amounts in India (in rupees) - sorted for binary search
    private static final double[] COMMON_SCHOOL_AMOUNTS = {
            500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000,
            5500, 6000, 6500, 7000, 7500, 8000, 8500, 9000, 9500, 10000,
            11000, 12000, 13000, 14000, 15000, 20000, 25000, 30000, 35000, 40000, 50000
    };

    // School-related keywords - using HashSet for O(1) lookup
    private static final Set<String> SCHOOL_KEYWORDS = new HashSet<>(Arrays.asList(
            "school", "tuition", "fee", "fees", "payment", "college",
            "education", "academy", "institute", "springfield", "admission",
            "exam", "test", "term", "quarter", "semester", "annual",
            "hostel", "bus", "transport", "uniform", "book", "stationery",
            "library", "lab", "sports", "activity", "workshop", "trip",
            "medical", "development", "maintenance", "computer", "science"
    ));

    // False positive indicators - using HashSet for O(1) lookup
    private static final Set<String> FALSE_POSITIVES = new HashSet<>(Arrays.asList(
            "atm", "withdrawal", "cash withdrawal", "self transfer",
            "salary", "loan", "emi", "investment", "insurance",
            "shopping", "amazon", "flipkart", "swiggy", "zomato",
            "movie", "cinema", "petrol", "fuel", "diesel",
            "restaurant", "hotel", "flight", "travel", "holiday",
            "shopping", "mall", "market", "grocery", "vegetable",
            "mobile", "recharge", "dth", "electricity", "water",
            "gas", "lpg", "rent", "emi", "credit card", "bill"
    ));

    // Additional fee indicators
    private static final Set<String> ADDITIONAL_FEE_INDICATORS = new HashSet<>(Arrays.asList(
            "exam", "test", "transport", "bus", "uniform", "dress",
            "book", "stationery", "library", "lab", "sports", "game",
            "activity", "workshop", "trip", "tour", "medical", "health",
            "admission", "registration", "enrollment", "development",
            "maintenance", "computer", "science", "art", "music", "dance"
    ));

    // Pre-compiled patterns for name extraction
    private static final java.util.regex.Pattern NAME_PATTERN =
            java.util.regex.Pattern.compile("\\b[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+\\b");

    // Nickname mappings for common Indian names
    private static final Map<String, String[]> NICKNAME_MAP = createNicknameMap();

    private static Map<String, String[]> createNicknameMap() {
        Map<String, String[]> map = new HashMap<>();
        map.put("arjun", new String[]{"arun"});
        map.put("arun", new String[]{"arjun"});
        map.put("vivek", new String[]{"viv", "vive"});
        map.put("vikram", new String[]{"vik", "vikky"});
        map.put("priya", new String[]{"pri", "piu"});
        map.put("anjali", new String[]{"anju", "anj"});
        map.put("rohan", new String[]{"ro", "roh"});
        map.put("ishita", new String[]{"ish", "ishu"});
        map.put("sneha", new String[]{"snu"});
        map.put("kabir", new String[]{"kab"});
        return map;
    }

    // ========== PUBLIC METHODS ==========

    /**
     * Optimized matching using cache indices
     */
    public Optional<Student> findMatchingStudent(BankTransaction transaction, List<Student> students) {
        if (transaction == null || transaction.getDescription() == null) {
            return Optional.empty();
        }

        String description = transaction.getDescription().toLowerCase();
        log.debug("🔍 Matching transaction: {}", description);

        // ========== FIXED: Always use cache if loaded ==========
        if (cacheLoaded.get()) {
            // Use cache indices (O(1) operations)
            log.trace("Using cache for matching");

            // Strategy 1: Exact name match from cache (O(1))
            Optional<Student> exactMatch = findExactNameMatch(description);
            if (exactMatch.isPresent()) {
                log.trace("Exact name match found via cache");
                return exactMatch;
            }

            // Strategy 2: Name extraction and cache lookup
            String extractedName = extractStudentNameFromDescriptionOptimized(description);
            if (extractedName != null && !extractedName.trim().isEmpty()) {
                Optional<Student> extractedMatch = findByNameExtraction(extractedName, description);
                if (extractedMatch.isPresent()) {
                    log.trace("Name extraction match found via cache");
                    return extractedMatch;
                }
            }

            // Strategy 3: Amount-based matching
            if (transaction.getAmount() != null) {
                Optional<Student> amountMatch = findAmountMatch(transaction.getAmount(), description);
                if (amountMatch.isPresent()) {
                    log.trace("Amount match found via cache");
                    return amountMatch;
                }
            }

            // Strategy 4: Name parts matching
            Optional<Student> namePartMatch = findNamePartMatch(description);
            if (namePartMatch.isPresent()) {
                log.trace("Name part match found via cache");
                return namePartMatch;
            }

            log.trace("No match found in cache for: {}", description);
            return Optional.empty();
        }

        // ========== Fallback: No cache available ==========
        log.warn("Cache not loaded, using fallback matching with {} students",
                students != null ? students.size() : 0);

        if (students != null && !students.isEmpty()) {
            return findMatchingStudentFallback(transaction, students);
        }

        return Optional.empty();
    }

    /**
     * Optimized match score calculation
     */
    public Double calculateMatchScore(BankTransaction transaction, Student student, Double pendingFee) {
        long startTime = System.currentTimeMillis();

        double score = 0.0;
        String description = transaction.getDescription().toLowerCase();
        String studentName = student.getFullName().toLowerCase();
        String[] studentNameParts = studentName.split("\\s+");
        Double transactionAmount = transaction.getAmount();

        // ========== CATEGORY 1: NAME MATCHING (MAX 40 POINTS) ==========

        // 1A. Exact full name match in description (25 points)
        if (description.contains(studentName)) {
            score += 25;
        }

        // 1B. Name extraction match (20 points)
        String extractedName = extractStudentNameFromDescriptionOptimized(description);
        if (extractedName != null) {
            String extractedLower = extractedName.toLowerCase();
            if (extractedLower.equals(studentName)) {
                score += 20;
            } else if (extractedLower.contains(studentName) || studentName.contains(extractedLower)) {
                score += 15;
            }
        }

        // 1C. First + Last name combo (15 points)
        if (studentNameParts.length >= 2) {
            String firstName = studentNameParts[0];
            String lastName = studentNameParts[studentNameParts.length - 1];
            if (description.contains(firstName) && description.contains(lastName)) {
                score += 15;
            }
        }

        // 1D. First name only (5 points)
        if (studentNameParts.length > 0) {
            String firstName = studentNameParts[0];
            if (firstName.length() >= 3 && description.contains(firstName)) {
                score += 5;
            }
        }

        // ========== CATEGORY 2: AMOUNT MATCHING (MAX 40 POINTS) ==========

        if (pendingFee != null) {
            if (pendingFee > 0) {
                // Student has pending fees
                if (Math.abs(transactionAmount - pendingFee) < 0.01) {
                    score += 30;
                } else if (isCommonSchoolAmountOptimized(transactionAmount)) {
                    score += 25;
                } else if (transactionAmount < pendingFee && transactionAmount > 0) {
                    score += 20;
                } else if (Math.abs(transactionAmount - pendingFee) <= 500) {
                    score += 15;
                } else if (transactionAmount >= 500 && transactionAmount <= 50000) {
                    score += 10;
                }
            } else {
                // Student has NO pending fees
                if (isAdditionalFeeTransactionOptimized(description, transactionAmount)) {
                    score += 25;
                } else if (isCommonSchoolAmountOptimized(transactionAmount)) {
                    score += 20;
                } else if (transactionAmount >= 500 && transactionAmount <= 50000) {
                    score += 15;
                }
            }
        } else {
            // No fee info available
            if (isCommonSchoolAmountOptimized(transactionAmount)) {
                score += 15;
            } else if (transactionAmount >= 500 && transactionAmount <= 50000) {
                score += 10;
            }
        }

        // ========== CATEGORY 3: TRANSACTION CONTEXT (MAX 20 POINTS) ==========

        // 3A. School-related keywords (10 points)
        if (hasSchoolKeywordsOptimized(description)) {
            score += 10;
        }

        // 3B. Payment method confidence (5 points)
        if (isReliablePaymentMethod(transaction.getPaymentMethod())) {
            score += 5;
        }

        // 3C. Recent transaction (5 points)
        long daysOld = java.time.temporal.ChronoUnit.DAYS.between(
                transaction.getTransactionDate(), java.time.LocalDate.now());
        if (daysOld <= 30) {
            score += 5;
        } else if (daysOld <= 90) {
            score += 3;
        }

        // ========== CATEGORY 4: PENALTIES (DEDUCT POINTS) ==========

        // 4A. False positive indicators (severe penalty)
        if (isLikelyFalsePositiveOptimized(description)) {
            score -= 40;
        }

        // 4B. Unusual payment methods for schools
        if (isUnusualForSchool(transaction.getPaymentMethod())) {
            score -= 20;
        }

        // 4C. Very old transaction
        if (daysOld > 180) {
            score -= 15;
        }

        // 4D. Unusual amount for school
        if (transactionAmount < 100 || transactionAmount > 100000) {
            score -= 15;
        }

        // 4E. Generic description (small penalty)
        if (hasGenericDescriptionOptimized(description)) {
            score -= 5;
        }

        // Ensure score doesn't go negative
        score = Math.max(0, score);

        // Cap at 100
        score = Math.min(score, 100);

        long duration = System.currentTimeMillis() - startTime;
        if (duration > 10) {
            log.debug("Score calculation took {}ms for {}", duration, student.getFullName());
        }

        return score;
    }

    // ========== CACHE MANAGEMENT ==========

    /**
     * Initialize cache with all students
     */
    public void initializeCache(List<Student> students) {
        if (cacheLoaded.get()) {
            log.debug("Cache already loaded");
            return;
        }

        log.info("Building transaction matcher cache for {} students...", students.size());
        long startTime = System.currentTimeMillis();

        exactNameCache.clear();
        namePartIndex.clear();
        amountIndex.clear();

        for (Student student : students) {
            indexStudent(student);
        }

        cacheLoaded.set(true);
        long duration = System.currentTimeMillis() - startTime;

        log.info("✅ Transaction matcher cache built in {}ms: {} names, {} name parts, {} amount entries",
                duration, exactNameCache.size(), namePartIndex.size(), amountIndex.size());
    }

    /**
     * Refresh cache with updated student list
     */
    public void refreshCache(List<Student> students) {
        log.info("Refreshing transaction matcher cache...");
        cacheLoaded.set(false);
        initializeCache(students);
    }

    /**
     * Check if cache is loaded
     */
    public boolean isCacheLoaded() {
        return cacheLoaded.get();
    }

    // ========== OPTIMIZED HELPER METHODS ==========

    private void indexStudent(Student student) {
        String fullNameLower = student.getFullName().toLowerCase();

        // Index by exact name
        exactNameCache.put(fullNameLower, student);

        // Index by name parts
        String[] nameParts = fullNameLower.split("\\s+");
        for (String part : nameParts) {
            if (part.length() > 2) {
                namePartIndex.computeIfAbsent(part, k -> new ArrayList<>())
                        .add(student);
            }
        }

        // Index by pending amount (rounded to nearest 100)
        if (student.getPendingAmount() != null && student.getPendingAmount() > 0) {
            String amountKey = String.valueOf(Math.round(student.getPendingAmount() / 100.0) * 100);
            amountIndex.computeIfAbsent(amountKey, k -> new ArrayList<>())
                    .add(student);
        }
    }

    private Optional<Student> findExactNameMatch(String description) {
        // Quick scan through cached names
        for (Map.Entry<String, Student> entry : exactNameCache.entrySet()) {
            if (description.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    private Optional<Student> findByNameExtraction(String extractedName, String description) {
        String extractedLower = extractedName.toLowerCase();

        // Try exact match first
        Student student = exactNameCache.get(extractedLower);
        if (student != null) {
            return Optional.of(student);
        }

        // Try partial matches
        for (Map.Entry<String, Student> entry : exactNameCache.entrySet()) {
            String cachedName = entry.getKey();
            if (cachedName.contains(extractedLower) || extractedLower.contains(cachedName)) {
                return Optional.of(entry.getValue());
            }
        }

        return Optional.empty();
    }

    private Optional<Student> findNamePartMatch(String description) {
        String[] words = description.split("\\W+");

        for (String word : words) {
            if (word.length() > 2) {
                List<Student> candidates = namePartIndex.get(word.toLowerCase());
                if (candidates != null && !candidates.isEmpty()) {
                    // Find candidate whose full name appears in description
                    for (Student candidate : candidates) {
                        if (description.contains(candidate.getFullName().toLowerCase())) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private Optional<Student> findAmountMatch(Double amount, String description) {
        if (amount == null) return Optional.empty();

        String amountKey = String.valueOf(Math.round(amount / 100.0) * 100);
        List<Student> candidates = amountIndex.get(amountKey);

        if (candidates != null && !candidates.isEmpty()) {
            for (Student candidate : candidates) {
                if (description.contains(candidate.getFullName().toLowerCase())) {
                    return Optional.of(candidate);
                }
            }
        }

        return Optional.empty();
    }

    private String extractStudentNameFromDescriptionOptimized(String description) {
        // Use regex for faster name extraction
        java.util.regex.Matcher matcher = NAME_PATTERN.matcher(description);
        if (matcher.find()) {
            return matcher.group();
        }

        // Fallback to original logic
        String[] words = description.split("\\W+");
        List<String> nameParts = new ArrayList<>();

        for (String word : words) {
            if (word.length() > 2 && Character.isUpperCase(word.charAt(0))) {
                nameParts.add(word);
            }
        }

        if (nameParts.size() >= 2) {
            return nameParts.get(0) + " " + nameParts.get(1);
        } else if (!nameParts.isEmpty()) {
            return nameParts.get(0);
        }

        return null;
    }

    private boolean isCommonSchoolAmountOptimized(Double amount) {
        if (amount == null) return false;

        // Binary search on sorted array
        int index = Arrays.binarySearch(COMMON_SCHOOL_AMOUNTS, amount);
        if (index >= 0) {
            return true;
        }

        // Check within small tolerance
        for (double common : COMMON_SCHOOL_AMOUNTS) {
            if (Math.abs(amount - common) < 0.01) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSchoolKeywordsOptimized(String description) {
        // Quick scan for school keywords
        for (String keyword : SCHOOL_KEYWORDS) {
            if (description.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLikelyFalsePositiveOptimized(String description) {
        // Quick scan for false positives
        for (String fp : FALSE_POSITIVES) {
            if (description.contains(fp)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAdditionalFeeTransactionOptimized(String description, Double amount) {
        if (description == null || amount == null) return false;

        // Check for additional fee indicators
        for (String indicator : ADDITIONAL_FEE_INDICATORS) {
            if (description.contains(indicator)) {
                // Additional fees are usually smaller
                return amount >= 500 && amount <= 10000;
            }
        }
        return false;
    }

    private boolean hasGenericDescriptionOptimized(String description) {
        if (description == null) return false;

        String[] genericTerms = {
                "payment", "transfer", "transaction", "fund", "amount",
                "credit", "debit", "received", "paid", "sent"
        };

        // Count generic terms
        int genericCount = 0;
        for (String term : genericTerms) {
            if (description.contains(term)) {
                genericCount++;
            }
        }

        return genericCount >= 2 && description.split(" ").length <= 4;
    }

    // ========== FALLBACK METHODS ==========

    private Optional<Student> findMatchingStudentFallback(BankTransaction transaction, List<Student> students) {
        String description = transaction.getDescription().toLowerCase();

        // Original fallback logic
        String extractedName = extractStudentNameFromDescriptionOptimized(description);

        if (extractedName != null && !extractedName.trim().isEmpty()) {
            String extractedLower = extractedName.toLowerCase();

            for (Student student : students) {
                String fullName = student.getFullName().toLowerCase();

                if (fullName.equals(extractedLower)) {
                    return Optional.of(student);
                }

                if (fullName.contains(extractedLower) || extractedLower.contains(fullName)) {
                    return Optional.of(student);
                }
            }
        }

        return Optional.empty();
    }

    // ========== EXISTING HELPER METHODS (KEPT AS IS) ==========

    private boolean isReliablePaymentMethod(com.system.SchoolManagementSystem.transaction.enums.PaymentMethod method) {
        if (method == null) return false;

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

    private boolean isUnusualForSchool(com.system.SchoolManagementSystem.transaction.enums.PaymentMethod method) {
        if (method == null) return false;

        switch (method) {
            case CASH:
                return true;
            default:
                return false;
        }
    }

    public boolean isAmountMatch(Double transactionAmount, Double expectedAmount, Double tolerancePercentage) {
        if (expectedAmount == null) return false;

        double tolerance = expectedAmount * (tolerancePercentage / 100);
        double minAmount = expectedAmount - tolerance;
        double maxAmount = expectedAmount + tolerance;

        return transactionAmount >= minAmount && transactionAmount <= maxAmount;
    }
}