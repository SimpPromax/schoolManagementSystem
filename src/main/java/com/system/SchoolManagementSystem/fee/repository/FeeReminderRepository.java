package com.system.SchoolManagementSystem.fee.repository;

import com.system.SchoolManagementSystem.fee.entity.FeeReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FeeReminderRepository extends JpaRepository<FeeReminder, Long> {

    List<FeeReminder> findByStudentId(Long studentId);

    List<FeeReminder> findByStatus(String status);

    List<FeeReminder> findByReminderTypeAndStatus(String reminderType, String status);

    @Query("SELECT COUNT(fr) FROM FeeReminder fr WHERE fr.student.id = :studentId AND fr.status = 'SENT'")
    Long countSentRemindersForStudent(@Param("studentId") Long studentId);

    @Query("SELECT fr FROM FeeReminder fr WHERE fr.scheduledFor <= :now AND fr.status = 'PENDING'")
    List<FeeReminder> findPendingScheduledReminders(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(fr) FROM FeeReminder fr WHERE DATE(fr.createdAt) = CURRENT_DATE AND fr.status = 'SENT'")
    Long countTodayReminders();

    @Query("SELECT MAX(fr.sentAt) FROM FeeReminder fr " +
            "WHERE fr.student.id = :studentId AND fr.status = 'SENT'")
    LocalDateTime getLastReminderDate(@Param("studentId") Long studentId);
}