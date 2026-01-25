package com.system.SchoolManagementSystem.termmanagement.repository;

import com.system.SchoolManagementSystem.termmanagement.entity.GradeTermFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradeTermFeeRepository extends JpaRepository<GradeTermFee, Long> {

    Optional<GradeTermFee> findByAcademicTermIdAndGrade(Long academicTermId, String grade);

    List<GradeTermFee> findByAcademicTermId(Long academicTermId);

    List<GradeTermFee> findByGrade(String grade);

    List<GradeTermFee> findByIsActiveTrue();

    @Query("SELECT g FROM GradeTermFee g WHERE g.academicTerm.academicYear = :academicYear AND g.isActive = true")
    List<GradeTermFee> findByAcademicYear(@Param("academicYear") String academicYear);

    @Query("SELECT g FROM GradeTermFee g WHERE g.academicTerm.id = :termId AND g.grade = :grade AND g.isActive = true")
    Optional<GradeTermFee> findActiveFeeForTermAndGrade(@Param("termId") Long termId,
                                                        @Param("grade") String grade);

    @Query("SELECT DISTINCT g.grade FROM GradeTermFee g WHERE g.academicTerm.id = :termId ORDER BY g.grade")
    List<String> findDistinctGradesByTermId(@Param("termId") Long termId);

    @Query("SELECT g FROM GradeTermFee g WHERE g.academicTerm.isCurrent = true AND g.grade = :grade")
    Optional<GradeTermFee> findCurrentTermFeeForGrade(@Param("grade") String grade);

    @Query("SELECT SUM(g.totalFee) FROM GradeTermFee g WHERE g.academicTerm.id = :termId")
    Double getTotalExpectedRevenueForTerm(@Param("termId") Long termId);

    boolean existsByAcademicTermIdAndGrade(Long academicTermId, String grade);
}