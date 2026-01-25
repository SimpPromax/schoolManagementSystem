package com.system.SchoolManagementSystem.termmanagement.repository;

import com.system.SchoolManagementSystem.termmanagement.entity.AcademicTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Long> {

    // ========== BASIC CRUD & FINDERS ==========

    Optional<AcademicTerm> findByTermCode(String termCode);

    List<AcademicTerm> findByAcademicYear(String academicYear);

    List<AcademicTerm> findByStatus(AcademicTerm.TermStatus status);

    // UPDATED: Safe method to find by isCurrent (handles null)
    @Query("SELECT t FROM AcademicTerm t WHERE COALESCE(t.isCurrent, false) = true")
    Optional<AcademicTerm> findByIsCurrentTrue();

    // ========== DATE-BASED QUERIES ==========

    @Query("SELECT t FROM AcademicTerm t WHERE :date BETWEEN t.startDate AND t.endDate")
    Optional<AcademicTerm> findTermByDate(@Param("date") LocalDate date);

    @Query("SELECT t FROM AcademicTerm t WHERE t.startDate <= :endDate AND t.endDate >= :startDate")
    List<AcademicTerm> findOverlappingTerms(@Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);

    @Query("SELECT t FROM AcademicTerm t WHERE t.status = 'ACTIVE'")
    List<AcademicTerm> findActiveTerms();

    // UPDATED: Safe method to find current term
    @Query("SELECT t FROM AcademicTerm t WHERE COALESCE(t.isCurrent, false) = true")
    Optional<AcademicTerm> findCurrentTerm();

    @Query("SELECT t FROM AcademicTerm t WHERE t.feeDueDate < :today AND t.status IN ('ACTIVE', 'UPCOMING')")
    List<AcademicTerm> findTermsWithOverdueFees(@Param("today") LocalDate today);

    // ========== ACADEMIC YEAR QUERIES ==========

    @Query("SELECT DISTINCT t.academicYear FROM AcademicTerm t ORDER BY t.academicYear DESC")
    List<String> findAllAcademicYears();

    @Query("SELECT t FROM AcademicTerm t WHERE t.endDate >= :startDate ORDER BY t.startDate ASC")
    List<AcademicTerm> findUpcomingAndCurrentTerms(@Param("startDate") LocalDate startDate);

    // ========== TERM BREAKS QUERIES ==========

    @Query("SELECT t FROM AcademicTerm t WHERE t.termBreaks IS NOT NULL AND t.termBreaks != '' AND t.termBreaks != '[]' AND t.status = 'ACTIVE'")
    List<AcademicTerm> findActiveTermsWithBreaks();

    @Query("SELECT t FROM AcademicTerm t WHERE t.academicYear = :academicYear AND t.termBreaks IS NOT NULL AND t.termBreaks != '' AND t.termBreaks != '[]' ORDER BY t.startDate ASC")
    List<AcademicTerm> findByAcademicYearWithBreaks(@Param("academicYear") String academicYear);

    @Query("SELECT t FROM AcademicTerm t WHERE t.id = :termId AND t.termBreaks IS NOT NULL AND t.termBreaks != '' AND t.termBreaks != '[]'")
    Optional<AcademicTerm> findTermWithBreaks(@Param("termId") Long termId);

    // ========== ORDERED QUERIES ==========

    @Query("SELECT t FROM AcademicTerm t WHERE t.academicYear = :academicYear ORDER BY t.startDate ASC")
    List<AcademicTerm> findByAcademicYearOrderByStartDate(@Param("academicYear") String academicYear);

    @Query("SELECT t FROM AcademicTerm t ORDER BY t.startDate DESC")
    List<AcademicTerm> findAllOrderByStartDateDesc();

    @Query("SELECT t FROM AcademicTerm t WHERE t.academicYear = :academicYear ORDER BY t.startDate DESC")
    List<AcademicTerm> findByAcademicYearOrderByStartDateDesc(@Param("academicYear") String academicYear);

    // ========== STATISTICS & ANALYTICS QUERIES ==========

    @Query("SELECT COUNT(t) FROM AcademicTerm t WHERE t.academicYear = :academicYear")
    Long countByAcademicYear(@Param("academicYear") String academicYear);

    @Query("SELECT COUNT(t) FROM AcademicTerm t WHERE t.academicYear = :academicYear AND t.status = :status")
    Long countByAcademicYearAndStatus(@Param("academicYear") String academicYear,
                                      @Param("status") AcademicTerm.TermStatus status);

    // UPDATED: Safe count for current terms
    @Query("SELECT COUNT(t) FROM AcademicTerm t WHERE COALESCE(t.isCurrent, false) = true")
    Long countCurrentTerms();

    @Query("SELECT COUNT(t) FROM AcademicTerm t WHERE t.status = 'COMPLETED' AND t.academicYear = :academicYear")
    Long countCompletedTermsByYear(@Param("academicYear") String academicYear);

    // ========== RANGE QUERIES ==========

    @Query("SELECT t FROM AcademicTerm t WHERE t.startDate >= :startDate AND t.endDate <= :endDate ORDER BY t.startDate ASC")
    List<AcademicTerm> findTermsWithinDateRange(@Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    @Query("SELECT t FROM AcademicTerm t WHERE t.feeDueDate BETWEEN :startDate AND :endDate")
    List<AcademicTerm> findTermsWithFeeDueBetween(@Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);

    // ========== SEARCH QUERIES ==========

    @Query("SELECT t FROM AcademicTerm t WHERE LOWER(t.termName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(t.academicYear) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY t.startDate DESC")
    List<AcademicTerm> searchTerms(@Param("query") String query);

    // ========== VALIDATION QUERIES ==========

    @Query("SELECT COUNT(t) > 0 FROM AcademicTerm t WHERE t.academicYear = :academicYear AND t.termName = :termName")
    boolean existsByAcademicYearAndTermName(@Param("academicYear") String academicYear,
                                            @Param("termName") String termName);

    @Query("SELECT COUNT(t) > 0 FROM AcademicTerm t WHERE t.academicYear = :academicYear AND t.id != :excludeId AND t.termName = :termName")
    boolean existsByAcademicYearAndTermNameExcluding(@Param("academicYear") String academicYear,
                                                     @Param("termName") String termName,
                                                     @Param("excludeId") Long excludeId);

    // ========== UPDATE QUERIES ==========

    // UPDATED: Safe update for current terms
    @Modifying
    @Query("UPDATE AcademicTerm t SET t.isCurrent = false WHERE COALESCE(t.isCurrent, false) = true AND t.id != :excludeTermId")
    void deactivateOtherCurrentTerms(@Param("excludeTermId") Long excludeTermId);

    @Modifying
    @Query("UPDATE AcademicTerm t SET t.status = :status WHERE t.id = :termId")
    int updateTermStatus(@Param("termId") Long termId, @Param("status") AcademicTerm.TermStatus status);

    @Modifying
    @Query("UPDATE AcademicTerm t SET t.termBreaks = :termBreaks, t.termBreakDescription = :description WHERE t.id = :termId")
    int updateTermBreaks(@Param("termId") Long termId,
                         @Param("termBreaks") String termBreaks,
                         @Param("description") String description);

    // ========== SPECIFIC BUSINESS LOGIC QUERIES ==========

    @Query("SELECT t FROM AcademicTerm t WHERE t.status IN ('ACTIVE', 'UPCOMING') AND t.endDate >= CURRENT_DATE ORDER BY t.startDate ASC")
    List<AcademicTerm> findActiveAndUpcomingTerms();

    @Query("SELECT t FROM AcademicTerm t WHERE t.status = 'COMPLETED' AND t.academicYear = :academicYear ORDER BY t.endDate DESC")
    List<AcademicTerm> findCompletedTermsByYear(@Param("academicYear") String academicYear);

    @Query("SELECT t FROM AcademicTerm t WHERE t.academicYear = :academicYear AND t.status = 'ACTIVE' ORDER BY t.startDate ASC")
    List<AcademicTerm> findActiveTermsByYear(@Param("academicYear") String academicYear);

    // UPDATED: Safe query for current term by year
    @Query("SELECT t FROM AcademicTerm t WHERE t.academicYear = :academicYear AND COALESCE(t.isCurrent, false) = true")
    Optional<AcademicTerm> findCurrentTermByAcademicYear(@Param("academicYear") String academicYear);

    // ========== FEE MANAGEMENT RELATED QUERIES ==========

    @Query("SELECT t FROM AcademicTerm t WHERE t.feeDueDate IS NOT NULL AND t.feeDueDate >= :startDate AND t.feeDueDate <= :endDate ORDER BY t.feeDueDate ASC")
    List<AcademicTerm> findTermsWithFeeDueSoon(@Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate);

    @Query("SELECT t FROM AcademicTerm t WHERE t.academicYear = :academicYear AND t.feeDueDate >= :fromDate ORDER BY t.feeDueDate ASC")
    List<AcademicTerm> findTermsWithUpcomingFeeDue(@Param("academicYear") String academicYear,
                                                   @Param("fromDate") LocalDate fromDate);

    // ========== TERM BREAK SEARCH QUERIES ==========

    @Query("SELECT t FROM AcademicTerm t WHERE t.termBreaks LIKE CONCAT('%', :breakDate, '%')")
    List<AcademicTerm> findTermsContainingBreakDate(@Param("breakDate") String breakDate);

    @Query("SELECT t FROM AcademicTerm t WHERE t.termBreaks IS NOT NULL AND t.termBreaks != '' AND t.termBreaks != '[]' AND t.status = 'ACTIVE'")
    List<AcademicTerm> findActiveTermsWithNonEmptyBreaks();

    // ========== TERM DURATION QUERIES ==========

    @Query("SELECT t FROM AcademicTerm t WHERE FUNCTION('DATEDIFF', t.endDate, t.startDate) >= :minDays AND FUNCTION('DATEDIFF', t.endDate, t.startDate) <= :maxDays")
    List<AcademicTerm> findTermsByDuration(@Param("minDays") Integer minDays,
                                           @Param("maxDays") Integer maxDays);

    // ========== BULK OPERATIONS ==========

    @Modifying
    @Query("UPDATE AcademicTerm t SET t.status = :newStatus WHERE t.academicYear = :academicYear AND t.status = :oldStatus")
    int updateStatusForAcademicYear(@Param("academicYear") String academicYear,
                                    @Param("oldStatus") AcademicTerm.TermStatus oldStatus,
                                    @Param("newStatus") AcademicTerm.TermStatus newStatus);

    // ========== LATEST/NEXT TERM QUERIES ==========

    @Query("SELECT t FROM AcademicTerm t WHERE t.startDate > CURRENT_DATE ORDER BY t.startDate ASC")
    List<AcademicTerm> findNextUpcomingTerm();

    @Query(value = "SELECT * FROM academic_terms t WHERE t.end_date < CURRENT_DATE ORDER BY t.end_date DESC LIMIT 1", nativeQuery = true)
    Optional<AcademicTerm> findLastCompletedTerm();

    @Query("SELECT t FROM AcademicTerm t WHERE t.academicYear = :academicYear AND t.startDate > :date ORDER BY t.startDate ASC")
    List<AcademicTerm> findNextTermInAcademicYear(@Param("academicYear") String academicYear,
                                                  @Param("date") LocalDate date);

    // ========== TERM SCHEDULE CONFLICT QUERIES ==========

    @Query("SELECT COUNT(t) > 0 FROM AcademicTerm t WHERE t.id != :excludeId AND " +
            "((t.startDate BETWEEN :startDate AND :endDate) OR " +
            "(t.endDate BETWEEN :startDate AND :endDate) OR " +
            "(:startDate BETWEEN t.startDate AND t.endDate) OR " +
            "(:endDate BETWEEN t.startDate AND t.endDate))")
    boolean hasDateConflict(@Param("startDate") LocalDate startDate,
                            @Param("endDate") LocalDate endDate,
                            @Param("excludeId") Long excludeId);

    // ========== TERM SUMMARY QUERIES ==========

    @Query("SELECT t.academicYear, COUNT(t) as termCount, MIN(t.startDate) as earliestStart, MAX(t.endDate) as latestEnd " +
            "FROM AcademicTerm t GROUP BY t.academicYear ORDER BY t.academicYear DESC")
    List<Object[]> getAcademicYearSummary();

    // ========== ADDITIONAL SAFE QUERIES ==========

    // Find all terms with null-safe isCurrent check
    @Query("SELECT t FROM AcademicTerm t WHERE COALESCE(t.isCurrent, false) = :isCurrent")
    List<AcademicTerm> findByIsCurrentSafe(@Param("isCurrent") Boolean isCurrent);

    // Find current or active terms
    @Query("SELECT t FROM AcademicTerm t WHERE COALESCE(t.isCurrent, false) = true OR t.status = 'ACTIVE'")
    List<AcademicTerm> findCurrentOrActiveTerms();
}