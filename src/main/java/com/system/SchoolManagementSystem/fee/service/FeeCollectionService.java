package com.system.SchoolManagementSystem.fee.service;

import com.system.SchoolManagementSystem.fee.dto.request.*;
import com.system.SchoolManagementSystem.fee.dto.response.*;
import com.system.SchoolManagementSystem.fee.entity.FeeDashboardCache;
import com.system.SchoolManagementSystem.fee.entity.FeeReminder;
import com.system.SchoolManagementSystem.fee.enums.ReminderChannel;
import com.system.SchoolManagementSystem.fee.enums.ReminderTemplate;
import com.system.SchoolManagementSystem.fee.repository.FeeDashboardCacheRepository;
import com.system.SchoolManagementSystem.fee.repository.FeeReminderRepository;
import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.transaction.entity.PaymentTransaction;
import com.system.SchoolManagementSystem.transaction.enums.PaymentMethod;
import com.system.SchoolManagementSystem.transaction.repository.PaymentTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class FeeCollectionService {

    private final StudentRepository studentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final FeeReminderRepository feeReminderRepository;
    private final FeeDashboardCacheRepository feeDashboardCacheRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    // Cache duration in minutes
    private static final long CACHE_DURATION_MINUTES = 15;

    // ========== DASHBOARD STATISTICS ==========

    public FeeCollectionStatsResponse getDashboardStats() {
        String cacheKey = "dashboard_stats_" + LocalDate.now();

        // Try to get from cache first
        try {
            Optional<FeeDashboardCache> cachedData = feeDashboardCacheRepository.findByCacheKey(cacheKey);
            if (cachedData.isPresent() && cachedData.get().getExpiresAt().isAfter(LocalDateTime.now())) {
                return objectMapper.readValue(cachedData.get().getCacheData(), FeeCollectionStatsResponse.class);
            }
        } catch (Exception e) {
            log.warn("Failed to parse cached data, recalculating", e);
        }

        // Calculate fresh data
        FeeCollectionStatsResponse stats = calculateDashboardStats();

        // Cache the result
        try {
            cacheDashboardStats(cacheKey, stats);
        } catch (Exception e) {
            log.error("Failed to cache dashboard stats", e);
        }

        return stats;
    }

    private FeeCollectionStatsResponse calculateDashboardStats() {
        FeeCollectionStatsResponse stats = new FeeCollectionStatsResponse();

        try {
            // Use optimized queries instead of fetching all data
            long totalStudents = studentRepository.count();

            // Get total fee using repository query
            Double totalFee = studentRepository.getTotalFeeSum();
            Double totalPaid = paymentTransactionRepository.getTotalVerifiedAmount();
            Double totalPending = (totalFee != null && totalPaid != null) ?
                    totalFee - totalPaid : (totalFee != null ? totalFee : 0.0);

            // Get student counts by status using optimized query
            Map<String, Long> statusCounts = getStudentCountsByFeeStatus();

            // Set basic values
            stats.setTotalCollected(totalPaid != null ? round(totalPaid, 2) : 0.0);
            stats.setTotalPending(round(totalPending, 2));
            stats.setTotalFee(totalFee != null ? round(totalFee, 2) : 0.0);

            // Calculate collection rate
            if (totalFee != null && totalFee > 0 && totalPaid != null) {
                double rate = (totalPaid / totalFee) * 100;
                stats.setCollectionRate(round(rate, 2));
            } else {
                stats.setCollectionRate(0.0);
            }

            // Set status counts
            stats.setPaidStudents(statusCounts.getOrDefault("PAID", 0L));
            stats.setOverdueStudents(statusCounts.getOrDefault("OVERDUE", 0L));
            stats.setPendingStudents(statusCounts.getOrDefault("PENDING", 0L));

            // Calculate partial payments
            Long partialCount = studentRepository.countPartialPayments();
            stats.setPartialPaidStudents(partialCount != null ? partialCount : 0L);

            // Get students with multiple payments
            Long multiplePaymentStudents = paymentTransactionRepository.countStudentsWithMultiplePayments();
            stats.setMultiplePaymentStudents(multiplePaymentStudents != null ? multiplePaymentStudents : 0L);

            // Calculate average payments per student
            Long totalTransactions = paymentTransactionRepository.count();
            double avgPaymentsPerStudent = totalStudents > 0 ?
                    (totalTransactions != null ? totalTransactions.doubleValue() : 0.0) / totalStudents : 0.0;
            stats.setAveragePaymentsPerStudent(round(avgPaymentsPerStudent, 2));

            // Today's collection
            Double todayCollection = paymentTransactionRepository.getTotalVerifiedAmountToday();
            stats.setTodayCollection(todayCollection != null ? round(todayCollection, 2) : 0.0);

            // Weekly collection
            LocalDateTime weekStart = LocalDate.now().minusDays(7).atStartOfDay();
            Double weeklyCollection = paymentTransactionRepository.getTotalVerifiedAmountSince(weekStart);
            stats.setWeeklyCollection(weeklyCollection != null ? round(weeklyCollection, 2) : 0.0);

            // Monthly collection
            LocalDateTime monthStart = LocalDate.now().minusDays(30).atStartOfDay();
            Double monthlyCollection = paymentTransactionRepository.getTotalVerifiedAmountSince(monthStart);
            stats.setMonthlyCollection(monthlyCollection != null ? round(monthlyCollection, 2) : 0.0);

            // Reminders sent today
            Long remindersSentToday = feeReminderRepository.countTodayReminders();
            stats.setRemindersSentToday(remindersSentToday != null ? remindersSentToday : 0L);

            Long totalRemindersSent = feeReminderRepository.count();
            stats.setTotalRemindersSent(totalRemindersSent != null ? totalRemindersSent : 0L);

            // Grade-wise collection
            Map<String, Double> gradeWiseCollection = new HashMap<>();
            List<Object[]> gradeResults = paymentTransactionRepository.getCollectionByGradeJpa();
            if (gradeResults != null) {
                for (Object[] result : gradeResults) {
                    if (result[0] != null && result[1] != null) {
                        String grade = result[0].toString();
                        Double amount = (Double) result[1];
                        gradeWiseCollection.put(grade, amount != null ? round(amount, 2) : 0.0);
                    }
                }
            }
            stats.setGradeWiseCollection(gradeWiseCollection);

            // Status distribution
            Map<String, Long> statusDistribution = new HashMap<>();
            statusCounts.forEach((status, count) -> statusDistribution.put(status, count));
            stats.setStatusDistribution(statusDistribution);

            // Target achievement - removed mock values
            stats.setTargetAchievementRate(0.0);
            stats.setRemainingTarget(0.0);

        } catch (Exception e) {
            log.error("Error calculating dashboard stats", e);
            // Set default values in case of error
            stats.setTotalCollected(0.0);
            stats.setTotalPending(0.0);
            stats.setTotalFee(0.0);
            stats.setCollectionRate(0.0);
            stats.setPaidStudents(0L);
            stats.setOverdueStudents(0L);
            stats.setPendingStudents(0L);
            stats.setPartialPaidStudents(0L);
            stats.setMultiplePaymentStudents(0L);
            stats.setAveragePaymentsPerStudent(0.0);
            stats.setTodayCollection(0.0);
            stats.setWeeklyCollection(0.0);
            stats.setMonthlyCollection(0.0);
            stats.setRemindersSentToday(0L);
            stats.setTotalRemindersSent(0L);
            stats.setGradeWiseCollection(new HashMap<>());
            stats.setStatusDistribution(new HashMap<>());
            stats.setTargetAchievementRate(0.0);
            stats.setRemainingTarget(0.0);
        }

        return stats;
    }

    private Map<String, Long> getStudentCountsByFeeStatus() {
        Map<String, Long> counts = new HashMap<>();

        try {
            List<Object[]> results = studentRepository.countStudentsByFeeStatus();
            if (results != null) {
                for (Object[] result : results) {
                    if (result[0] != null && result[1] != null) {
                        String status;
                        if (result[0] instanceof Student.FeeStatus) {
                            status = ((Student.FeeStatus) result[0]).name();
                        } else {
                            status = result[0].toString();
                        }

                        Long count;
                        if (result[1] instanceof Long) {
                            count = (Long) result[1];
                        } else if (result[1] instanceof Integer) {
                            count = ((Integer) result[1]).longValue();
                        } else if (result[1] instanceof Number) {
                            count = ((Number) result[1]).longValue();
                        } else {
                            count = 0L;
                        }

                        counts.put(status, count);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting student counts by fee status", e);
        }

        // Ensure all statuses are present in the map (even if count is 0)
        for (Student.FeeStatus status : Student.FeeStatus.values()) {
            counts.putIfAbsent(status.name(), 0L);
        }

        return counts;
    }

    private void cacheDashboardStats(String cacheKey, FeeCollectionStatsResponse stats) {
        try {
            String jsonData = objectMapper.writeValueAsString(stats);

            FeeDashboardCache cache = FeeDashboardCache.builder()
                    .cacheKey(cacheKey)
                    .cacheType("STATS")
                    .cacheData(jsonData)
                    .dataDate(LocalDate.now())
                    .expiresAt(LocalDateTime.now().plusMinutes(CACHE_DURATION_MINUTES))
                    .updatedAt(LocalDateTime.now())
                    .build();

            feeDashboardCacheRepository.save(cache);
            log.debug("Successfully cached dashboard stats with key: {}", cacheKey);
        } catch (Exception e) {
            log.error("Failed to cache dashboard stats for key: {}", cacheKey, e);
            // Don't throw - caching is optional
        }
    }

    // ========== COLLECTION TREND ==========

    public CollectionTrendResponse getCollectionTrend(String period) {
        String cacheKey = "collection_trend_" + period + "_" + LocalDate.now();

        try {
            Optional<FeeDashboardCache> cachedData = feeDashboardCacheRepository.findByCacheKey(cacheKey);
            if (cachedData.isPresent() && cachedData.get().getExpiresAt().isAfter(LocalDateTime.now())) {
                return objectMapper.readValue(cachedData.get().getCacheData(), CollectionTrendResponse.class);
            }
        } catch (Exception e) {
            log.warn("Failed to parse cached trend data", e);
        }

        CollectionTrendResponse trend = calculateCollectionTrend(period);

        try {
            cacheTrendData(cacheKey, trend);
        } catch (Exception e) {
            log.error("Failed to cache trend data", e);
        }

        return trend;
    }

    private CollectionTrendResponse calculateCollectionTrend(String period) {
        CollectionTrendResponse response = new CollectionTrendResponse();
        response.setPeriod(period);

        // Set date range based on period
        LocalDateTime endDateTime = LocalDateTime.now();
        LocalDateTime startDateTime;

        switch (period.toUpperCase()) {
            case "DAILY":
                startDateTime = LocalDate.now().minusDays(7).atStartOfDay();
                break;
            case "WEEKLY":
                startDateTime = LocalDate.now().minusWeeks(8).atStartOfDay();
                break;
            case "MONTHLY":
                startDateTime = LocalDate.now().minusMonths(6).atStartOfDay();
                break;
            case "QUARTERLY":
                startDateTime = LocalDate.now().minusMonths(12).atStartOfDay();
                break;
            default:
                startDateTime = LocalDate.now().minusMonths(6).atStartOfDay();
        }

        response.setStartDate(startDateTime.toLocalDate());
        response.setEndDate(endDateTime.toLocalDate());

        List<CollectionTrendResponse.TrendDataPoint> dataPoints = new ArrayList<>();

        try {
            // Use the simple daily trend query (works for all periods with date conversion)
            List<Object[]> trendData = paymentTransactionRepository.getCollectionTrend(
                    startDateTime, endDateTime);

            if (trendData != null && !trendData.isEmpty()) {
                for (Object[] data : trendData) {
                    if (data != null && data.length >= 2) {
                        try {
                            CollectionTrendResponse.TrendDataPoint dataPoint =
                                    new CollectionTrendResponse.TrendDataPoint();

                            // Handle date
                            if (data[0] instanceof java.sql.Date) {
                                dataPoint.setDate(((java.sql.Date) data[0]).toLocalDate());
                            } else if (data[0] instanceof Date) {
                                dataPoint.setDate(((Date) data[0]).toInstant()
                                        .atZone(ZoneId.systemDefault()).toLocalDate());
                            } else if (data[0] != null) {
                                // Try to parse as string
                                try {
                                    String dateStr = data[0].toString();
                                    dataPoint.setDate(LocalDate.parse(dateStr));
                                } catch (Exception e) {
                                    log.debug("Could not parse date: {}", data[0]);
                                    continue; // Skip this data point if date is invalid
                                }
                            } else {
                                continue; // Skip if date is null
                            }

                            // Handle collected amount
                            if (data[1] != null) {
                                Double collected = ((Number) data[1]).doubleValue();
                                dataPoint.setCollectedAmount(round(collected, 2));
                                dataPoint.setTargetAmount(0.0); // Target feature not implemented yet
                                dataPoint.setOverdueAmount(0.0); // Overdue calculation requires separate logic
                            } else {
                                dataPoint.setCollectedAmount(0.0);
                                dataPoint.setTargetAmount(0.0);
                                dataPoint.setOverdueAmount(0.0);
                            }

                            dataPoint.setTransactionCount(0L); // Not implemented in query
                            dataPoint.setStudentCount(0L); // Not implemented in query

                            dataPoints.add(dataPoint);
                        } catch (Exception e) {
                            log.error("Error processing trend data point", e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching trend data for period: {}", period, e);
        }

        // Return empty list if no data (instead of sample data)
        response.setDataPoints(dataPoints);
        return response;
    }

    private void cacheTrendData(String cacheKey, CollectionTrendResponse trend) {
        try {
            String jsonData = objectMapper.writeValueAsString(trend);

            FeeDashboardCache cache = FeeDashboardCache.builder()
                    .cacheKey(cacheKey)
                    .cacheType("TREND")
                    .cacheData(jsonData)
                    .dataDate(LocalDate.now())
                    .expiresAt(LocalDateTime.now().plusMinutes(CACHE_DURATION_MINUTES))
                    .updatedAt(LocalDateTime.now())
                    .build();

            feeDashboardCacheRepository.save(cache);
        } catch (Exception e) {
            log.error("Failed to cache trend data for key: {}", cacheKey, e);
        }
    }

    public List<RecentPaymentResponse> getStudentRecentPayments(Long studentId, int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "paymentDate"));

            // Query payments for specific student only
            Page<PaymentTransaction> studentTransactions =
                    paymentTransactionRepository.findByStudentId(studentId, pageable);

            return studentTransactions.getContent().stream()
                    .map(this::convertToRecentPaymentResponse)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting recent payments for student: {}", studentId, e);
            return new ArrayList<>();
        }
    }

    // ========== PAYMENT METHOD DISTRIBUTION ==========

    public PaymentMethodDistributionResponse getPaymentMethodDistribution() {
        String cacheKey = "payment_methods_" + LocalDate.now();

        try {
            Optional<FeeDashboardCache> cachedData = feeDashboardCacheRepository.findByCacheKey(cacheKey);
            if (cachedData.isPresent() && cachedData.get().getExpiresAt().isAfter(LocalDateTime.now())) {
                return objectMapper.readValue(cachedData.get().getCacheData(), PaymentMethodDistributionResponse.class);
            }
        } catch (Exception e) {
            log.warn("Failed to parse cached payment methods data", e);
        }

        PaymentMethodDistributionResponse distribution = calculatePaymentMethodDistribution();

        try {
            cachePaymentMethodData(cacheKey, distribution);
        } catch (Exception e) {
            log.error("Failed to cache payment method data", e);
        }

        return distribution;
    }

    private PaymentMethodDistributionResponse calculatePaymentMethodDistribution() {
        PaymentMethodDistributionResponse response = new PaymentMethodDistributionResponse();
        response.setPeriod("ALL");

        long totalTransactions = 0;
        Double totalAmount = 0.0;
        List<PaymentMethodDistributionResponse.PaymentMethodData> paymentMethods = new ArrayList<>();

        try {
            List<Object[]> methodData = paymentTransactionRepository.getPaymentMethodDistribution();

            if (methodData != null) {
                for (Object[] data : methodData) {
                    if (data != null && data.length >= 3) {
                        try {
                            PaymentMethodDistributionResponse.PaymentMethodData method =
                                    new PaymentMethodDistributionResponse.PaymentMethodData();

                            // Handle PaymentMethod enum correctly
                            String methodName;
                            if (data[0] instanceof PaymentMethod) {
                                methodName = ((PaymentMethod) data[0]).name();
                            } else {
                                methodName = data[0] != null ? data[0].toString() : "UNKNOWN";
                            }

                            Long count = data[1] != null ? ((Number) data[1]).longValue() : 0L;
                            Double amount = data[2] != null ? ((Double) data[2]) : 0.0;

                            method.setMethod(methodName);
                            method.setDisplayName(getPaymentMethodDisplayName(methodName));
                            method.setTransactionCount(count);
                            method.setTotalAmount(amount != null ? round(amount, 2) : 0.0);
                            method.setColor(getPaymentMethodColor(methodName));

                            totalTransactions += count;
                            if (amount != null) {
                                totalAmount += amount;
                            }

                            paymentMethods.add(method);
                        } catch (Exception e) {
                            log.error("Error processing payment method data", e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error calculating payment method distribution", e);
        }

        // Calculate percentages
        for (PaymentMethodDistributionResponse.PaymentMethodData method : paymentMethods) {
            if (totalAmount > 0 && method.getTotalAmount() > 0) {
                double percentage = (method.getTotalAmount() / totalAmount) * 100;
                method.setPercentage(round(percentage, 2));
            } else {
                method.setPercentage(0.0);
            }
        }

        // Return empty list if no data
        response.setTotalTransactions(totalTransactions);
        response.setTotalAmount(round(totalAmount, 2));
        response.setPaymentMethods(paymentMethods);

        return response;
    }

    private String getPaymentMethodDisplayName(String method) {
        try {
            if (method == null) return "Unknown";

            PaymentMethod paymentMethod = PaymentMethod.valueOf(method.toUpperCase());
            switch (paymentMethod) {
                case ONLINE_BANKING: return "Online Banking";
                case CREDIT_CARD: return "Credit Card";
                case DEBIT_CARD: return "Debit Card";
                case UPI: return "UPI";
                case CASH: return "Cash";
                case CHEQUE: return "Cheque";
                case BANK_TRANSFER: return "Bank Transfer";
                case NEFT: return "NEFT";
                case RTGS: return "RTGS";
                case IMPS: return "IMPS";
                default: return method;
            }
        } catch (IllegalArgumentException e) {
            return method != null ? method : "Unknown";
        }
    }

    private String getPaymentMethodColor(String method) {
        try {
            if (method == null) return "#6b7280";

            PaymentMethod paymentMethod = PaymentMethod.valueOf(method.toUpperCase());
            switch (paymentMethod) {
                case ONLINE_BANKING: return "#3b82f6"; // Blue
                case UPI: return "#8b5cf6"; // Purple
                case CREDIT_CARD: case DEBIT_CARD: return "#10b981"; // Green
                case CASH: return "#f59e0b"; // Amber
                case CHEQUE: return "#6b7280"; // Gray
                case BANK_TRANSFER: return "#6366f1"; // Indigo
                case NEFT: case RTGS: case IMPS: return "#8b5cf6"; // Purple
                default: return "#8b5cf6"; // Default purple
            }
        } catch (IllegalArgumentException e) {
            return "#8b5cf6"; // Default purple
        }
    }

    private void cachePaymentMethodData(String cacheKey, PaymentMethodDistributionResponse distribution) {
        try {
            String jsonData = objectMapper.writeValueAsString(distribution);

            FeeDashboardCache cache = FeeDashboardCache.builder()
                    .cacheKey(cacheKey)
                    .cacheType("PAYMENT_METHODS")
                    .cacheData(jsonData)
                    .dataDate(LocalDate.now())
                    .expiresAt(LocalDateTime.now().plusMinutes(CACHE_DURATION_MINUTES))
                    .updatedAt(LocalDateTime.now())
                    .build();

            feeDashboardCacheRepository.save(cache);
        } catch (Exception e) {
            log.error("Failed to cache payment method data for key: {}", cacheKey, e);
        }
    }

    // ========== OVERDUE DISTRIBUTION ==========

    public OverdueDistributionResponse getOverdueDistribution() {
        OverdueDistributionResponse response = new OverdueDistributionResponse();

        try {
            // Get overdue distribution from repository
            List<Object[]> overdueData = studentRepository.getOverdueDistribution();

            long totalOverdueStudents = 0;
            Double totalOverdueAmount = 0.0;
            List<OverdueDistributionResponse.OverdueRange> ranges = new ArrayList<>();

            if (overdueData != null) {
                for (Object[] data : overdueData) {
                    if (data != null && data.length >= 3) {
                        try {
                            OverdueDistributionResponse.OverdueRange range =
                                    new OverdueDistributionResponse.OverdueRange();

                            range.setRange(data[0] != null ? data[0].toString() : "Unknown");
                            range.setStudentCount(data[1] != null ? ((Number) data[1]).longValue() : 0L);

                            Double amount = data[2] != null ? ((Double) data[2]) : 0.0;
                            range.setTotalAmount(amount != null ? round(amount, 2) : 0.0);

                            totalOverdueStudents += range.getStudentCount();
                            if (amount != null) {
                                totalOverdueAmount += amount;
                            }

                            ranges.add(range);
                        } catch (Exception e) {
                            log.error("Error processing overdue data", e);
                        }
                    }
                }
            }

            // Calculate percentages
            for (OverdueDistributionResponse.OverdueRange range : ranges) {
                if (totalOverdueStudents > 0) {
                    double percentage = (double) range.getStudentCount() / totalOverdueStudents * 100;
                    range.setPercentage(round(percentage, 2));
                } else {
                    range.setPercentage(0.0);
                }
            }

            response.setTotalOverdueStudents(totalOverdueStudents);
            response.setTotalOverdueAmount(round(totalOverdueAmount, 2));
            response.setOverdueRanges(ranges);

        } catch (Exception e) {
            log.error("Error calculating overdue distribution", e);
            // Return empty response
            response.setTotalOverdueStudents(0L);
            response.setTotalOverdueAmount(0.0);
            response.setOverdueRanges(new ArrayList<>());
        }

        return response;
    }

    // ========== RECENT PAYMENTS ==========

    public List<RecentPaymentResponse> getRecentPayments(int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "paymentDate"));
            Page<PaymentTransaction> recentTransactions = paymentTransactionRepository.findAll(pageable);

            return recentTransactions.getContent().stream()
                    .map(this::convertToRecentPaymentResponse)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting recent payments", e);
            return new ArrayList<>();
        }
    }

    private RecentPaymentResponse convertToRecentPaymentResponse(PaymentTransaction transaction) {
        RecentPaymentResponse response = new RecentPaymentResponse();
        response.setId(transaction.getId());
        response.setReceiptNumber(transaction.getReceiptNumber());
        response.setAmount(transaction.getAmount());
        response.setPaymentMethod(transaction.getPaymentMethod().name());
        response.setPaymentMethodDisplay(getPaymentMethodDisplayName(transaction.getPaymentMethod().name()));
        response.setPaymentDate(transaction.getPaymentDate());
        response.setVerified(transaction.getIsVerified());
        response.setVerifiedBy(transaction.getVerifiedBy() != null ?
                transaction.getVerifiedBy().getFullName() : "System");
        response.setVerifiedAt(transaction.getVerifiedAt());

        if (transaction.getStudent() != null) {
            response.setStudentId(transaction.getStudent().getId());
            response.setStudentName(transaction.getStudent().getFullName());
            response.setStudentGrade(transaction.getStudent().getGrade());
        } else {
            response.setStudentId(0L);
            response.setStudentName("Unknown");
            response.setStudentGrade("N/A");
        }

        response.setBankReference(transaction.getBankReference());
        response.setPaymentFor(transaction.getPaymentFor());
        response.setNotes(transaction.getNotes());

        return response;
    }

    // ========== STUDENT FEE MANAGEMENT ==========

    public Page<StudentFeeSummaryResponse> getFilteredStudents(FeeCollectionFilterRequest filterRequest) {
        try {
            // Create specification for filtering
            Specification<Student> spec = buildStudentFilterSpecification(filterRequest);

            // Create pageable with sorting
            Sort sort = Sort.by(Sort.Direction.fromString(filterRequest.getSortDirection()),
                    filterRequest.getSortBy());
            Pageable pageable = PageRequest.of(filterRequest.getPage(), filterRequest.getSize(), sort);

            // Execute query with specification
            Page<Student> studentPage = studentRepository.findAll(spec, pageable);

            // Convert to response
            List<StudentFeeSummaryResponse> responses = studentPage.getContent().stream()
                    .map(this::convertToStudentFeeSummaryResponse)
                    .collect(Collectors.toList());

            return new PageImpl<>(responses, pageable, studentPage.getTotalElements());
        } catch (Exception e) {
            log.error("Error filtering students", e);
            return new PageImpl<>(new ArrayList<>());
        }
    }

    private Specification<Student> buildStudentFilterSpecification(FeeCollectionFilterRequest filterRequest) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by grade
            if (filterRequest.getGrade() != null && !filterRequest.getGrade().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("grade"), filterRequest.getGrade()));
            }

            // Filter by fee status
            if (filterRequest.getFeeStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("feeStatus"), filterRequest.getFeeStatus()));
            }

            // Filter by search query
            if (filterRequest.getSearchQuery() != null && !filterRequest.getSearchQuery().isEmpty()) {
                String searchPattern = "%" + filterRequest.getSearchQuery().toLowerCase() + "%";
                Predicate namePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("fullName")), searchPattern);
                Predicate emailPredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("email")), searchPattern);
                Predicate phonePredicate = criteriaBuilder.like(root.get("phone"), searchPattern);
                predicates.add(criteriaBuilder.or(namePredicate, emailPredicate, phonePredicate));
            }

            // Filter by date range (admission date)
            if (filterRequest.getFromDate() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("admissionDate"), filterRequest.getFromDate()));
            }

            if (filterRequest.getToDate() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("admissionDate"), filterRequest.getToDate()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public StudentFeeSummaryResponse convertToStudentFeeSummaryResponse(Student student) {
        StudentFeeSummaryResponse response = new StudentFeeSummaryResponse();

        try {
            response.setStudentId(student.getId());
            response.setStudentName(student.getFullName());
            response.setStudentIdNumber(student.getStudentId());
            response.setGrade(student.getGrade());
            response.setGuardianName(student.getEmergencyContactName());
            response.setContact(student.getPhone());
            response.setEmail(student.getEmail());

            response.setTotalFee(student.getTotalFee() != null ? student.getTotalFee() : 0.0);
            response.setPaidAmount(student.getPaidAmount() != null ? student.getPaidAmount() : 0.0);
            response.setPendingAmount(student.getPendingAmount() != null ? student.getPendingAmount() : 0.0);

            if (student.getTotalFee() != null && student.getTotalFee() > 0) {
                double paymentPercentage = ((student.getPaidAmount() != null ? student.getPaidAmount() : 0.0) /
                        student.getTotalFee() * 100);
                response.setPaymentPercentage(round(paymentPercentage, 2));
            } else {
                response.setPaymentPercentage(0.0);
            }

            response.setFeeStatus(student.getFeeStatus());

            // Get last payment date using repository query
            LocalDateTime lastPayment = paymentTransactionRepository.getLastPaymentDate(student.getId());
            if (lastPayment != null) {
                response.setLastPaymentDate(lastPayment.toLocalDate());
            }

            // Get payment count and methods
            Long paymentCount = paymentTransactionRepository.countByStudentId(student.getId());
            response.setPaymentCount(paymentCount != null ? paymentCount.intValue() : 0);

            List<String> paymentMethods = paymentTransactionRepository.getPaymentMethodsByStudentId(student.getId());
            response.setPaymentMethodsUsed(paymentMethods != null ? paymentMethods : new ArrayList<>());
            response.setHasMultiplePayments(paymentCount != null && paymentCount > 1);

            // Calculate average payment amount
            Double averagePayment = paymentTransactionRepository.getAveragePaymentByStudentId(student.getId());
            response.setAveragePaymentAmount(averagePayment != null ? round(averagePayment, 2) : 0.0);

            // Get reminders sent
            Long remindersSent = feeReminderRepository.countSentRemindersForStudent(student.getId());
            response.setRemindersSent(remindersSent != null ? remindersSent.intValue() : 0);

            // Get last reminder date
            LocalDateTime lastReminder = feeReminderRepository.getLastReminderDate(student.getId());
            if (lastReminder != null) {
                response.setLastReminderDate(lastReminder.toLocalDate());
            }
        } catch (Exception e) {
            log.error("Error converting student to fee summary response: {}", student.getId(), e);
        }

        return response;
    }

    public StudentPaymentHistoryResponse getStudentPaymentHistory(Long studentId) {
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found with id: " + studentId));

            // Get transactions with optimized query
            List<PaymentTransaction> studentTransactions =
                    paymentTransactionRepository.findByStudentIdOrderByPaymentDateDesc(studentId);

            StudentPaymentHistoryResponse response = new StudentPaymentHistoryResponse();
            response.setStudentId(studentId);
            response.setStudentName(student.getFullName());
            response.setStudentGrade(student.getGrade());
            response.setTotalFee(student.getTotalFee() != null ? student.getTotalFee() : 0.0);
            response.setTotalPaid(student.getPaidAmount() != null ? student.getPaidAmount() : 0.0);
            response.setTotalPending(student.getPendingAmount() != null ? student.getPendingAmount() : 0.0);

            if (student.getTotalFee() != null && student.getTotalFee() > 0) {
                double percentage = (response.getTotalPaid() / response.getTotalFee()) * 100;
                response.setPaymentProgress(String.format("%.1f%%", percentage));
            } else {
                response.setPaymentProgress("0.0%");
            }

            // Convert transactions
            List<StudentPaymentHistoryResponse.PaymentTransactionDetail> transactionDetails =
                    studentTransactions.stream()
                            .map(this::convertToPaymentTransactionDetail)
                            .collect(Collectors.toList());

            response.setTransactions(transactionDetails);

            // Calculate summary
            StudentPaymentHistoryResponse.PaymentSummary summary = new StudentPaymentHistoryResponse.PaymentSummary();
            summary.setTotalTransactions(studentTransactions.size());

            if (!studentTransactions.isEmpty()) {
                PaymentTransaction firstPayment = studentTransactions.get(studentTransactions.size() - 1);
                PaymentTransaction lastPayment = studentTransactions.get(0);

                summary.setFirstPayment(firstPayment.getPaymentDate());
                summary.setDaysSinceFirstPayment(ChronoUnit.DAYS.between(
                        firstPayment.getPaymentDate().toLocalDate(), LocalDate.now()
                ));
                summary.setLastPayment(lastPayment.getPaymentDate());

                List<String> paymentMethods = studentTransactions.stream()
                        .map(t -> getPaymentMethodDisplayName(t.getPaymentMethod().name()))
                        .distinct()
                        .collect(Collectors.toList());
                summary.setPaymentMethods(paymentMethods);

                double avgPayment = studentTransactions.stream()
                        .mapToDouble(PaymentTransaction::getAmount)
                        .average()
                        .orElse(0.0);
                summary.setAveragePaymentAmount(round(avgPayment, 2));
            } else {
                summary.setTotalTransactions(0);
                summary.setAveragePaymentAmount(0.0);
                summary.setPaymentMethods(new ArrayList<>());
            }

            response.setSummary(summary);
            return response;
        } catch (Exception e) {
            log.error("Error getting student payment history: {}", studentId, e);
            throw new RuntimeException("Failed to get student payment history: " + e.getMessage());
        }
    }

    private StudentPaymentHistoryResponse.PaymentTransactionDetail convertToPaymentTransactionDetail(
            PaymentTransaction transaction) {
        StudentPaymentHistoryResponse.PaymentTransactionDetail detail =
                new StudentPaymentHistoryResponse.PaymentTransactionDetail();

        detail.setId(transaction.getId());
        detail.setReceiptNumber(transaction.getReceiptNumber());
        detail.setAmount(transaction.getAmount());
        detail.setPaymentMethod(transaction.getPaymentMethod().name());
        detail.setPaymentMethodDisplay(getPaymentMethodDisplayName(transaction.getPaymentMethod().name()));
        detail.setPaymentDate(transaction.getPaymentDate());
        detail.setVerifiedBy(transaction.getVerifiedBy() != null ?
                transaction.getVerifiedBy().getFullName() : "System");
        detail.setNotes(transaction.getNotes());

        // Remove mock fee breakdown - return empty list
        detail.setBreakdown(new ArrayList<>());
        return detail;
    }

    // ========== REMINDER MANAGEMENT ==========

    @Async
    public void sendEmailReminder(ReminderRequest request) {
        try {
            Student student = studentRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            // Create reminder record
            FeeReminder reminder = FeeReminder.builder()
                    .student(student)
                    .reminderType("EMAIL")
                    .channel(student.getEmail())
                    .content(request.getCustomContent() != null ?
                            request.getCustomContent() :
                            generateDefaultEmailContent(student, request.getTemplate()))
                    .templateName(request.getTemplate() != null ?
                            request.getTemplate().name() : "DEFAULT")
                    .status("PENDING")
                    .scheduledFor(request.getScheduleFor() != null ?
                            request.getScheduleFor() : LocalDateTime.now())
                    .build();

            feeReminderRepository.save(reminder);

            // Send email (simulated for now)
            boolean success = emailService.sendFeeReminderEmail(student, reminder);

            if (success) {
                reminder.setStatus("SENT");
                reminder.setSentAt(LocalDateTime.now());
                reminder.setGatewayResponse("Email sent successfully");
                reminder.setGatewayMessageId("EMAIL_" + System.currentTimeMillis());
            } else {
                reminder.setStatus("FAILED");
                reminder.setGatewayResponse("Failed to send email");
            }

            feeReminderRepository.save(reminder);

        } catch (Exception e) {
            log.error("Failed to send email reminder", e);
            throw new RuntimeException("Failed to send email reminder: " + e.getMessage());
        }
    }

    @Async
    public void sendSmsReminder(ReminderRequest request) {
        try {
            Student student = studentRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            FeeReminder reminder = FeeReminder.builder()
                    .student(student)
                    .reminderType("SMS")
                    .channel(student.getPhone())
                    .content(request.getCustomContent() != null ?
                            request.getCustomContent() :
                            generateDefaultSmsContent(student, request.getTemplate()))
                    .templateName(request.getTemplate() != null ?
                            request.getTemplate().name() : "DEFAULT")
                    .status("PENDING")
                    .scheduledFor(request.getScheduleFor() != null ?
                            request.getScheduleFor() : LocalDateTime.now())
                    .build();

            feeReminderRepository.save(reminder);

            // Simulate SMS sending
            reminder.setStatus("SENT");
            reminder.setSentAt(LocalDateTime.now());
            reminder.setGatewayMessageId("SMS_" + System.currentTimeMillis());
            reminder.setGatewayResponse("SMS queued for delivery");

            feeReminderRepository.save(reminder);

        } catch (Exception e) {
            log.error("Failed to send SMS reminder", e);
            throw new RuntimeException("Failed to send SMS reminder: " + e.getMessage());
        }
    }

    public BulkReminderResultResponse sendBulkReminders(BulkReminderRequest request) {
        BulkReminderResultResponse response = new BulkReminderResultResponse();
        response.setTotalSelected(request.getStudentIds().size());

        List<BulkReminderResultResponse.ReminderResult> results = new ArrayList<>();
        int successCount = 0;
        double totalPendingAmount = 0.0;

        for (Long studentId : request.getStudentIds()) {
            try {
                Student student = studentRepository.findById(studentId)
                        .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

                totalPendingAmount += student.getPendingAmount() != null ?
                        student.getPendingAmount() : 0.0;

                // Create reminder based on channel
                FeeReminder reminder;
                if (request.getChannel() == ReminderChannel.EMAIL) {
                    reminder = createEmailReminder(student, request);
                } else {
                    reminder = createSmsReminder(student, request);
                }

                feeReminderRepository.save(reminder);
                successCount++;

                BulkReminderResultResponse.ReminderResult result =
                        new BulkReminderResultResponse.ReminderResult();
                result.setStudentId(studentId);
                result.setStudentName(student.getFullName());
                result.setChannel(request.getChannel().name());
                result.setStatus("SENT");
                result.setMessage("Reminder sent successfully");
                results.add(result);

            } catch (Exception e) {
                log.error("Failed to send reminder for student {}", studentId, e);

                BulkReminderResultResponse.ReminderResult result =
                        new BulkReminderResultResponse.ReminderResult();
                result.setStudentId(studentId);
                result.setChannel(request.getChannel().name());
                result.setStatus("FAILED");
                result.setError(e.getMessage());
                results.add(result);
            }
        }

        response.setSuccessfullySent(successCount);
        response.setFailed(request.getStudentIds().size() - successCount);
        response.setTotalPendingAmount(totalPendingAmount);
        response.setResults(results);

        return response;
    }

    private FeeReminder createEmailReminder(Student student, BulkReminderRequest request) {
        return FeeReminder.builder()
                .student(student)
                .reminderType("EMAIL")
                .channel(student.getEmail())
                .content(request.getCustomContent() != null ?
                        request.getCustomContent() :
                        generateDefaultEmailContent(student, request.getTemplate()))
                .templateName(request.getTemplate() != null ?
                        request.getTemplate().name() : "DEFAULT")
                .status("SENT") // Assume sent for bulk operation
                .sentAt(LocalDateTime.now())
                .gatewayMessageId("BULK_EMAIL_" + System.currentTimeMillis() + "_" + student.getId())
                .gatewayResponse("Bulk email reminder sent")
                .build();
    }

    private FeeReminder createSmsReminder(Student student, BulkReminderRequest request) {
        return FeeReminder.builder()
                .student(student)
                .reminderType("SMS")
                .channel(student.getPhone())
                .content(request.getCustomContent() != null ?
                        request.getCustomContent() :
                        generateDefaultSmsContent(student, request.getTemplate()))
                .templateName(request.getTemplate() != null ?
                        request.getTemplate().name() : "DEFAULT")
                .status("SENT") // Assume sent for bulk operation
                .sentAt(LocalDateTime.now())
                .gatewayMessageId("BULK_SMS_" + System.currentTimeMillis() + "_" + student.getId())
                .gatewayResponse("Bulk SMS reminder sent")
                .build();
    }

    private String generateDefaultEmailContent(Student student, ReminderTemplate template) {
        String baseContent = "Dear Parent/Guardian,\n\n";

        if (template == ReminderTemplate.OVERDUE) {
            baseContent += String.format("URGENT: School fee for %s (Class: %s) is OVERDUE.\n\n" +
                            "Pending Amount: ₹%.2f\n" +
                            "Original Due Date: %s\n\n" +
                            "Please make the payment immediately to avoid any late fees or penalties.\n\n" +
                            "Payment Methods:\n" +
                            "1. Online Payment Portal\n" +
                            "2. Bank Transfer\n" +
                            "3. UPI Payment\n" +
                            "4. Cash at School Office\n\n" +
                            "For any queries, contact the accounts department.\n\n" +
                            "Best regards,\nSchool Accounts Department",
                    student.getFullName(),
                    student.getGrade(),
                    student.getPendingAmount() != null ? student.getPendingAmount() : 0.0,
                    student.getFeeDueDate() != null ? student.getFeeDueDate().toString() : "N/A");
        } else {
            baseContent += String.format("Gentle reminder: School fee for %s (Class: %s) is pending.\n\n" +
                            "Total Fee: ₹%.2f\n" +
                            "Paid Amount: ₹%.2f\n" +
                            "Pending Amount: ₹%.2f\n" +
                            "Due Date: %s\n\n" +
                            "Please complete the payment at your earliest convenience.\n\n" +
                            "Thank you,\nSchool Accounts Department",
                    student.getFullName(),
                    student.getGrade(),
                    student.getTotalFee() != null ? student.getTotalFee() : 0.0,
                    student.getPaidAmount() != null ? student.getPaidAmount() : 0.0,
                    student.getPendingAmount() != null ? student.getPendingAmount() : 0.0,
                    student.getFeeDueDate() != null ? student.getFeeDueDate().toString() : "N/A");
        }

        return baseContent;
    }

    private String generateDefaultSmsContent(Student student, ReminderTemplate template) {
        if (template == ReminderTemplate.OVERDUE) {
            return String.format("URGENT: Fee for %s overdue. Pending: ₹%.2f. Pay immediately. School Accounts",
                    student.getFullName(),
                    student.getPendingAmount() != null ? student.getPendingAmount() : 0.0);
        } else {
            return String.format("Reminder: Fee for %s pending. Amount: ₹%.2f. Due: %s. School Accounts",
                    student.getFullName(),
                    student.getPendingAmount() != null ? student.getPendingAmount() : 0.0,
                    student.getFeeDueDate() != null ? student.getFeeDueDate().toString() : "N/A");
        }
    }

    // ========== SCHEDULED TASKS ==========

    @Scheduled(cron = "0 0 9 * * ?") // Run daily at 9 AM
    public void sendDailyOverdueReminders() {
        log.info("Starting daily overdue reminder process...");

        try {
            List<Student> overdueStudents = studentRepository.findOverdueStudents(LocalDate.now());

            for (Student student : overdueStudents) {
                try {
                    ReminderRequest request = new ReminderRequest();
                    request.setStudentId(student.getId());
                    request.setChannel(ReminderChannel.SMS);
                    request.setTemplate(ReminderTemplate.OVERDUE);

                    sendSmsReminder(request);
                    log.info("Sent overdue reminder to student: {}", student.getFullName());

                } catch (Exception e) {
                    log.error("Failed to send overdue reminder to student {}", student.getId(), e);
                }
            }

            log.info("Completed daily overdue reminder process. Sent {} reminders.", overdueStudents.size());
        } catch (Exception e) {
            log.error("Error in daily overdue reminder process", e);
        }
    }

    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void cleanExpiredCache() {
        log.debug("Cleaning expired cache entries...");

        try {
            List<FeeDashboardCache> expiredCache = feeDashboardCacheRepository
                    .findExpiredCache(LocalDateTime.now());

            if (!expiredCache.isEmpty()) {
                feeDashboardCacheRepository.deleteAll(expiredCache);
                log.info("Cleaned {} expired cache entries", expiredCache.size());
            }
        } catch (Exception e) {
            log.error("Error cleaning expired cache", e);
        }
    }


    // ========== HELPER METHODS ==========

    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    // ========== GETTER FOR REPOSITORY (FOR CONTROLLER) ==========

    public StudentRepository getStudentRepository() {
        return studentRepository;
    }
}