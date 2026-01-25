package com.system.SchoolManagementSystem.termmanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system.SchoolManagementSystem.termmanagement.dto.request.*;
import com.system.SchoolManagementSystem.termmanagement.dto.response.*;
import com.system.SchoolManagementSystem.termmanagement.entity.AcademicTerm;
import com.system.SchoolManagementSystem.termmanagement.repository.AcademicTermRepository;
import com.system.SchoolManagementSystem.termmanagement.repository.StudentTermAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TermService {

    private final AcademicTermRepository academicTermRepository;
    private final StudentTermAssignmentRepository studentTermAssignmentRepository;

    // ========== BASIC CRUD OPERATIONS ==========

    /**
     * Create a new academic term
     */
    @Transactional
    public AcademicTerm createTerm(CreateTermRequest request) {
        // Check for overlapping terms
        List<AcademicTerm> overlappingTerms = academicTermRepository
                .findOverlappingTerms(request.getStartDate(), request.getEndDate());

        if (!overlappingTerms.isEmpty()) {
            throw new RuntimeException("Term dates overlap with existing term: " +
                    overlappingTerms.get(0).getTermName());
        }

        // Build the term with proper defaults
        AcademicTerm term = AcademicTerm.builder()
                .termName(request.getTermName())
                .academicYear(request.getAcademicYear())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .feeDueDate(request.getFeeDueDate())
                .isCurrent(request.getIsCurrent() != null ? request.getIsCurrent() : false)
                .termBreakDescription(request.getTermBreakDescription())
                .build();

        // Explicitly set status if not set
        if (term.getStatus() == null) {
            term.setStatus(AcademicTerm.TermStatus.UPCOMING);
        }

        log.info("Creating term: {} with status: {} and isCurrent: {}",
                term.getTermName(), term.getStatus(), term.getIsCurrent());

        // If setting as current, handle current term logic
        if (Boolean.TRUE.equals(term.getIsCurrent())) {
            return setTermAsCurrent(term);
        }

        return academicTermRepository.save(term);
    }

    /**
     * Get all academic terms with null safety
     */
    @Transactional(readOnly = true)
    public List<AcademicTerm> getAllTerms() {
        try {
            List<AcademicTerm> terms = academicTermRepository.findAllOrderByStartDateDesc();

            if (terms == null) {
                return new ArrayList<>();
            }

            // Ensure all fields have proper values
            terms.forEach(term -> {
                if (term.getStatus() == null) {
                    term.setStatus(AcademicTerm.TermStatus.UPCOMING);
                }
                if (term.getIsCurrent() == null) {
                    term.setIsCurrent(false);
                }
            });

            return terms;
        } catch (Exception e) {
            log.error("Error fetching all terms: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Get term by ID with null safety
     */
    @Transactional(readOnly = true)
    public Optional<AcademicTerm> getTermById(Long termId) {
        Optional<AcademicTerm> termOpt = academicTermRepository.findById(termId);

        termOpt.ifPresent(term -> {
            if (term.getIsCurrent() == null) {
                term.setIsCurrent(false);
            }
        });

        return termOpt;
    }

    /**
     * Get terms by academic year with null safety
     */
    @Transactional(readOnly = true)
    public List<AcademicTerm> getTermsByAcademicYear(String academicYear) {
        List<AcademicTerm> terms = academicTermRepository.findByAcademicYearOrderByStartDate(academicYear);

        // Ensure isCurrent is never null
        terms.forEach(term -> {
            if (term.getIsCurrent() == null) {
                term.setIsCurrent(false);
            }
        });

        return terms;
    }

    /**
     * Get current active term with multiple fallback strategies
     */
    @Transactional(readOnly = true)
    public Optional<AcademicTerm> getCurrentTerm() {
        try {
            // Method 1: Use repository query with COALESCE
            Optional<AcademicTerm> term = academicTermRepository.findCurrentTerm();

            if (term.isPresent()) {
                AcademicTerm currentTerm = term.get();
                // Ensure isCurrent is not null
                if (currentTerm.getIsCurrent() == null) {
                    currentTerm.setIsCurrent(false);
                    log.warn("Found term with null isCurrent, fixing: {}", currentTerm.getTermName());
                }
                return Optional.of(currentTerm);
            }

            // Method 2: Find active term by current date
            term = academicTermRepository.findTermByDate(LocalDate.now());

            if (term.isPresent()) {
                AcademicTerm activeTerm = term.get();
                // Mark it as current
                activeTerm.setIsCurrent(true);
                academicTermRepository.save(activeTerm);
                log.info("Auto-marked term as current based on date: {}", activeTerm.getTermName());
                return Optional.of(activeTerm);
            }

            // Method 3: Find any active term
            List<AcademicTerm> activeTerms = academicTermRepository.findActiveTerms();
            if (!activeTerms.isEmpty()) {
                AcademicTerm firstActive = activeTerms.get(0);
                // Mark first active term as current
                firstActive.setIsCurrent(true);
                academicTermRepository.save(firstActive);
                log.info("Auto-marked first active term as current: {}", firstActive.getTermName());
                return Optional.of(firstActive);
            }

            return Optional.empty();

        } catch (Exception e) {
            log.error("Error finding current term: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get all academic years (distinct)
     */
    @Transactional(readOnly = true)
    public List<String> getAllAcademicYears() {
        return academicTermRepository.findAllAcademicYears();
    }

    /**
     * Update existing term with null safety
     */
    @Transactional
    public AcademicTerm updateTerm(Long termId, UpdateTermRequest request) {
        AcademicTerm term = academicTermRepository.findById(termId)
                .orElseThrow(() -> new RuntimeException("Academic term not found with id: " + termId));

        // Check for overlapping terms (excluding current term)
        List<AcademicTerm> overlappingTerms = academicTermRepository
                .findOverlappingTerms(request.getStartDate(), request.getEndDate())
                .stream()
                .filter(t -> !t.getId().equals(termId))
                .collect(Collectors.toList());

        if (!overlappingTerms.isEmpty()) {
            throw new RuntimeException("Term dates overlap with existing term: " +
                    overlappingTerms.get(0).getTermName());
        }

        // Update term
        term.setTermName(request.getTermName());
        term.setAcademicYear(request.getAcademicYear());
        term.setStartDate(request.getStartDate());
        term.setEndDate(request.getEndDate());
        term.setFeeDueDate(request.getFeeDueDate());

        // Handle isCurrent with null safety
        term.setIsCurrent(request.getIsCurrent() != null ? request.getIsCurrent() : false);

        // If setting as current, handle current term logic
        if (Boolean.TRUE.equals(term.getIsCurrent())) {
            return setTermAsCurrent(term);
        }

        return academicTermRepository.save(term);
    }

    /**
     * Delete term
     */
    @Transactional
    public boolean deleteTerm(Long termId) {
        AcademicTerm term = academicTermRepository.findById(termId)
                .orElseThrow(() -> new RuntimeException("Academic term not found with id: " + termId));

        // Check if term has fee structures or assignments
        if (!term.getGradeTermFees().isEmpty()) {
            throw new RuntimeException("Cannot delete term with existing fee structures");
        }

        if (!term.getStudentTermAssignments().isEmpty()) {
            throw new RuntimeException("Cannot delete term with student assignments");
        }

        academicTermRepository.delete(term);
        return true;
    }

    /**
     * Set term as current term
     */
    @Transactional
    public AcademicTerm setCurrentTerm(Long termId) {
        AcademicTerm term = academicTermRepository.findById(termId)
                .orElseThrow(() -> new RuntimeException("Academic term not found with id: " + termId));

        return setTermAsCurrent(term);
    }

    /**
     * Set term as current term (internal helper with null safety)
     */
    private AcademicTerm setTermAsCurrent(AcademicTerm term) {
        // Deactivate all other terms as current
        List<AcademicTerm> currentTerms = academicTermRepository.findAll().stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsCurrent()))
                .collect(Collectors.toList());

        for (AcademicTerm currentTerm : currentTerms) {
            if (!currentTerm.getId().equals(term.getId())) {
                currentTerm.setIsCurrent(false);
                academicTermRepository.save(currentTerm);
            }
        }

        // Set this term as current
        term.setIsCurrent(true);
        term.setStatus(AcademicTerm.TermStatus.ACTIVE);
        return academicTermRepository.save(term);
    }

    // ========== ACADEMIC YEAR INITIALIZATION ==========

    /**
     * Initialize a complete academic year with multiple terms
     */
    @Transactional
    public AcademicYearInitializationResponse initializeAcademicYear(InitializeAcademicYearRequest request) {
        log.info("Initializing academic year: {}", request.getAcademicYear());

        // Check if academic year already exists
        List<String> existingYears = academicTermRepository.findAllAcademicYears();
        if (existingYears.contains(request.getAcademicYear())) {
            throw new RuntimeException("Academic year already exists: " + request.getAcademicYear());
        }

        // Validate term periods don't overlap
        validateTermPeriods(request.getTermPeriods());

        List<TermResponse> createdTerms = new ArrayList<>();

        // Create each term
        for (int i = 0; i < request.getTermPeriods().size(); i++) {
            InitializeAcademicYearRequest.TermPeriod termPeriod = request.getTermPeriods().get(i);

            AcademicTerm term = AcademicTerm.builder()
                    .termName(termPeriod.getTermName())
                    .academicYear(request.getAcademicYear())
                    .startDate(termPeriod.getStartDate())
                    .endDate(termPeriod.getEndDate())
                    .feeDueDate(termPeriod.getFeeDueDate())
                    .termBreakDescription(termPeriod.getTermBreakDescription())
                    // Set first term as current by default
                    .isCurrent(i == 0)
                    .build();

            // Set term breaks if provided
            if (termPeriod.getTermBreaks() != null && !termPeriod.getTermBreaks().isEmpty()) {
                // Validate break dates are within term period
                validateBreakDates(termPeriod.getTermBreaks(), termPeriod.getStartDate(), termPeriod.getEndDate());
                term.setTermBreakDates(termPeriod.getTermBreaks());
            }

            AcademicTerm savedTerm = academicTermRepository.save(term);

            // If this is the first term and should be current, set it as current
            if (i == 0) {
                savedTerm = setTermAsCurrent(savedTerm);
            }

            createdTerms.add(TermResponse.fromEntity(savedTerm));

            log.info("Created term: {} ({}) isCurrent: {}",
                    savedTerm.getTermName(), savedTerm.getAcademicYear(), savedTerm.getIsCurrent());
        }

        AcademicYearInitializationResponse response = new AcademicYearInitializationResponse();
        response.setAcademicYear(request.getAcademicYear());
        response.setCreatedTerms(createdTerms);
        response.setInitializedAt(LocalDateTime.now());
        response.setStatus("INITIALIZED");

        log.info("Successfully initialized academic year {} with {} terms",
                request.getAcademicYear(), createdTerms.size());

        return response;
    }

    /**
     * Get term breaks for a specific term
     */
    @Transactional(readOnly = true)
    public List<TermBreakResponse> getTermBreaks(Long termId) {
        AcademicTerm term = academicTermRepository.findById(termId)
                .orElseThrow(() -> new RuntimeException("Term not found with id: " + termId));

        List<LocalDate> breakDates = term.getTermBreakDates();
        List<TermBreakResponse> breaks = new ArrayList<>();

        if (breakDates != null && !breakDates.isEmpty()) {
            // Group consecutive dates
            List<List<LocalDate>> groupedBreaks = groupConsecutiveDates(breakDates);

            for (List<LocalDate> breakGroup : groupedBreaks) {
                TermBreakResponse breakResponse = new TermBreakResponse();
                breakResponse.setBreakDate(breakGroup.get(0));
                breakResponse.setEndDate(breakGroup.get(breakGroup.size() - 1));
                breakResponse.setDurationDays(breakGroup.size());

                // Set description
                if (term.getTermBreakDescription() != null && !term.getTermBreakDescription().isEmpty()) {
                    breakResponse.setDescription(term.getTermBreakDescription());
                } else if (breakGroup.size() == 1) {
                    breakResponse.setDescription("Single Day Break");
                } else {
                    breakResponse.setDescription("Multi-day Break");
                }

                breaks.add(breakResponse);
            }
        }

        return breaks;
    }

    /**
     * Get current academic year structure
     */
    @Transactional(readOnly = true)
    public AcademicYearStructureResponse getCurrentAcademicYearStructure() {
        Optional<AcademicTerm> currentTermOpt = getCurrentTerm();

        AcademicYearStructureResponse response = new AcademicYearStructureResponse();

        if (currentTermOpt.isPresent()) {
            AcademicTerm currentTerm = currentTermOpt.get();
            String currentAcademicYear = currentTerm.getAcademicYear();
            List<AcademicTerm> terms = getTermsByAcademicYear(currentAcademicYear);

            response.setAcademicYear(currentAcademicYear);
            response.setIsInitialized(!terms.isEmpty());
            response.setCurrentTermName(currentTerm.getTermName());
            response.setCurrentTermId(currentTerm.getId());

            List<AcademicYearStructureResponse.TermStructure> termStructures = new ArrayList<>();

            for (AcademicTerm term : terms) {
                AcademicYearStructureResponse.TermStructure structure =
                        new AcademicYearStructureResponse.TermStructure();
                structure.setTermId(term.getId());
                structure.setTermName(term.getTermName());
                structure.setStartDate(term.getStartDate());
                structure.setEndDate(term.getEndDate());
                structure.setFeeDueDate(term.getFeeDueDate());
                structure.setStatus(term.getStatus().name());
                structure.setIsCurrent(term.getIsCurrent());
                structure.setStudentCount(getStudentCountForTerm(term.getId()));
                structure.setExpectedRevenue(calculateTermExpectedRevenue(term.getId()));
                structure.setCollectedRevenue(calculateTermCollections(term.getId()));
                structure.setCollectionRate(structure.getExpectedRevenue() > 0 ?
                        (structure.getCollectedRevenue() / structure.getExpectedRevenue()) * 100 : 0);
                structure.setTermBreaks(term.getTermBreakDates());
                structure.setTermBreakDescription(term.getTermBreakDescription());
                structure.setWorkingDays(term.getWorkingDays());
                structure.setBreakDays(term.getBreakDayCount());

                termStructures.add(structure);
            }

            // Sort terms by start date
            termStructures.sort(Comparator.comparing(AcademicYearStructureResponse.TermStructure::getStartDate));
            response.setTerms(termStructures);
        } else {
            response.setIsInitialized(false);
            response.setMessage("No academic year has been initialized. Please initialize an academic year first.");
        }

        return response;
    }

    /**
     * Get academic year history
     */
    @Transactional(readOnly = true)
    public List<AcademicYearHistoryResponse> getAcademicYearHistory() {
        List<String> academicYears = academicTermRepository.findAllAcademicYears();
        List<AcademicYearHistoryResponse> history = new ArrayList<>();

        for (String academicYear : academicYears) {
            List<AcademicTerm> terms = getTermsByAcademicYear(academicYear);

            if (!terms.isEmpty()) {
                AcademicYearHistoryResponse yearHistory = new AcademicYearHistoryResponse();
                yearHistory.setAcademicYear(academicYear);
                yearHistory.setTerms(terms.stream()
                        .map(TermResponse::fromEntity)
                        .collect(Collectors.toList()));

                // Calculate statistics
                double totalCollections = 0;
                double totalExpected = 0;
                int totalStudents = 0;

                for (AcademicTerm term : terms) {
                    totalCollections += calculateTermCollections(term.getId());
                    totalExpected += calculateTermExpectedRevenue(term.getId());
                    totalStudents += getStudentCountForTerm(term.getId());
                }

                yearHistory.setTotalStudents(totalStudents);
                yearHistory.setTotalExpected(totalExpected);
                yearHistory.setTotalCollections(totalCollections);
                yearHistory.setCollectionRate(totalExpected > 0 ? (totalCollections / totalExpected) * 100 : 0);
                yearHistory.setIsCurrent(isCurrentAcademicYear(academicYear));
                yearHistory.setTermCount(terms.size());

                // Calculate academic year dates
                yearHistory.setYearStartDate(terms.get(0).getStartDate());
                yearHistory.setYearEndDate(terms.get(terms.size() - 1).getEndDate());

                history.add(yearHistory);
            }
        }

        // Sort by year descending (most recent first)
        history.sort((a, b) -> b.getAcademicYear().compareTo(a.getAcademicYear()));

        return history;
    }

    // ========== HELPER METHODS ==========

    /**
     * Validate term periods don't overlap
     */
    private void validateTermPeriods(List<InitializeAcademicYearRequest.TermPeriod> termPeriods) {
        for (int i = 0; i < termPeriods.size(); i++) {
            InitializeAcademicYearRequest.TermPeriod p1 = termPeriods.get(i);

            // Validate break dates are within term period
            validateBreakDates(p1.getTermBreaks(), p1.getStartDate(), p1.getEndDate());

            for (int j = i + 1; j < termPeriods.size(); j++) {
                InitializeAcademicYearRequest.TermPeriod p2 = termPeriods.get(j);

                if (periodsOverlap(p1, p2)) {
                    throw new RuntimeException(
                            String.format("Term periods overlap: %s (%s to %s) and %s (%s to %s)",
                                    p1.getTermName(), p1.getStartDate(), p1.getEndDate(),
                                    p2.getTermName(), p2.getStartDate(), p2.getEndDate()));
                }
            }
        }
    }

    /**
     * Check if two term periods overlap
     */
    private boolean periodsOverlap(InitializeAcademicYearRequest.TermPeriod p1,
                                   InitializeAcademicYearRequest.TermPeriod p2) {
        return !p1.getEndDate().isBefore(p2.getStartDate()) &&
                !p2.getEndDate().isBefore(p1.getStartDate());
    }

    /**
     * Validate break dates are within term dates
     */
    private void validateBreakDates(List<LocalDate> breakDates, LocalDate termStart, LocalDate termEnd) {
        if (breakDates != null) {
            for (LocalDate breakDate : breakDates) {
                if (breakDate.isBefore(termStart) || breakDate.isAfter(termEnd)) {
                    throw new RuntimeException(
                            String.format("Break date %s is outside term period (%s to %s)",
                                    breakDate, termStart, termEnd));
                }
            }
        }
    }

    /**
     * Group consecutive dates for term breaks
     */
    private List<List<LocalDate>> groupConsecutiveDates(List<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) {
            return new ArrayList<>();
        }

        List<LocalDate> sortedDates = dates.stream()
                .sorted()
                .collect(Collectors.toList());

        List<List<LocalDate>> groups = new ArrayList<>();
        List<LocalDate> currentGroup = new ArrayList<>();

        for (int i = 0; i < sortedDates.size(); i++) {
            currentGroup.add(sortedDates.get(i));

            if (i == sortedDates.size() - 1 ||
                    !sortedDates.get(i).plusDays(1).equals(sortedDates.get(i + 1))) {
                groups.add(new ArrayList<>(currentGroup));
                currentGroup.clear();
            }
        }

        return groups;
    }

    /**
     * Get student count for term
     */
    private int getStudentCountForTerm(Long termId) {
        Long count = studentTermAssignmentRepository.countByAcademicTermId(termId);
        return count != null ? count.intValue() : 0;
    }

    /**
     * Calculate total collections for term
     */
    private double calculateTermCollections(Long termId) {
        Double collected = studentTermAssignmentRepository.getTotalCollectedForTerm(termId);
        return collected != null ? collected : 0.0;
    }

    /**
     * Calculate expected revenue for term
     */
    private double calculateTermExpectedRevenue(Long termId) {
        Double expected = studentTermAssignmentRepository.getTotalExpectedRevenueForTerm(termId);
        return expected != null ? expected : 0.0;
    }

    /**
     * Check if academic year is current
     */
    private boolean isCurrentAcademicYear(String academicYear) {
        Optional<AcademicTerm> current = getCurrentTerm();
        return current.isPresent() && current.get().getAcademicYear().equals(academicYear);
    }

    /**
     * Update term status (runs daily at midnight)
     * Automatically detects and updates current/upcoming/completed terms with null safety
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void updateTermStatus() {
        log.info("🔄 Updating term statuses...");

        List<AcademicTerm> allTerms = academicTermRepository.findAll();

        for (AcademicTerm term : allTerms) {
            LocalDate today = LocalDate.now();

            // Ensure isCurrent is not null
            if (term.getIsCurrent() == null) {
                term.setIsCurrent(false);
            }

            if (term.isActiveTerm()) {
                // This term should be current
                if (!term.getIsCurrent()) {
                    term.setIsCurrent(true);
                    term.setStatus(AcademicTerm.TermStatus.ACTIVE);
                    log.info("Activated term: {}", term.getTermName());
                }
            } else {
                // This term should not be current
                if (term.getIsCurrent()) {
                    term.setIsCurrent(false);
                }

                // Update status based on dates
                if (today.isAfter(term.getEndDate())) {
                    term.setStatus(AcademicTerm.TermStatus.COMPLETED);
                } else if (today.isBefore(term.getStartDate())) {
                    term.setStatus(AcademicTerm.TermStatus.UPCOMING);
                }
            }
        }

        academicTermRepository.saveAll(allTerms);
        log.info("✅ Term status update completed");
    }

    /**
     * Find overlapping terms with given date range
     */
    public List<AcademicTerm> findOverlappingTerms(LocalDate startDate, LocalDate endDate) {
        return academicTermRepository.findOverlappingTerms(startDate, endDate);
    }

    /**
     * Get upcoming and current terms
     */
    public List<AcademicTerm> getUpcomingAndCurrentTerms() {
        return academicTermRepository.findUpcomingAndCurrentTerms(LocalDate.now());
    }

    /**
     * Check if term exists
     */
    public boolean termExists(Long termId) {
        return academicTermRepository.existsById(termId);
    }

    /**
     * Validate term dates don't overlap with existing terms
     */
    public boolean validateTermDates(LocalDate startDate, LocalDate endDate, Long excludeTermId) {
        List<AcademicTerm> overlapping = academicTermRepository
                .findOverlappingTerms(startDate, endDate);

        if (excludeTermId != null) {
            overlapping.removeIf(term -> term.getId().equals(excludeTermId));
        }

        return overlapping.isEmpty();
    }

    /**
     * Update term breaks for a specific term
     */
    @Transactional
    public TermBreakUpdateResponse updateTermBreaks(Long termId, TermBreakUpdateRequest request) {
        AcademicTerm term = academicTermRepository.findById(termId)
                .orElseThrow(() -> new RuntimeException("Term not found with id: " + termId));

        // Validate break dates are within term period
        validateBreakDates(request.getTermBreaks(), term.getStartDate(), term.getEndDate());

        List<LocalDate> previousBreaks = term.getTermBreakDates();
        term.setTermBreakDates(request.getTermBreaks());
        term.setTermBreakDescription(request.getBreakDescription());

        AcademicTerm updatedTerm = academicTermRepository.save(term);

        TermBreakUpdateResponse response = new TermBreakUpdateResponse();
        response.setTermId(termId);
        response.setTermName(term.getTermName());
        response.setPreviousBreaks(previousBreaks);
        response.setUpdatedBreaks(updatedTerm.getTermBreakDates());
        response.setBreakDescription(updatedTerm.getTermBreakDescription());
        response.setUpdatedAt(LocalDateTime.now());
        response.setMessage("Term breaks updated successfully");

        return response;
    }

    /**
     * Fix all null isCurrent values in database (one-time fix)
     */
    @Transactional
    public void fixNullIsCurrentValues() {
        log.info("🔧 Fixing null isCurrent values in database...");

        List<AcademicTerm> allTerms = academicTermRepository.findAll();
        int fixedCount = 0;

        for (AcademicTerm term : allTerms) {
            if (term.getIsCurrent() == null) {
                term.setIsCurrent(false);
                fixedCount++;
            }
        }

        if (fixedCount > 0) {
            academicTermRepository.saveAll(allTerms);
            log.info("✅ Fixed {} terms with null isCurrent values", fixedCount);
        } else {
            log.info("✅ No null isCurrent values found");
        }
    }
}