package com.system.SchoolManagementSystem.fee.entity;

import com.system.SchoolManagementSystem.student.entity.Student;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "fee_reminders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FeeReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "reminder_type", nullable = false, length = 20)
    private String reminderType; // EMAIL, SMS, WHATSAPP, IN_APP

    @Column(name = "channel", nullable = false, length = 50)
    private String channel; // email, phone, whatsapp_number

    @Column(name = "content", length = 2000)
    private String content;

    @Column(name = "template_name", length = 100)
    private String templateName;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING, SENT, DELIVERED, FAILED

    @Column(name = "gateway_response", length = 1000)
    private String gatewayResponse;

    @Column(name = "gateway_message_id", length = 100)
    private String gatewayMessageId;

    @Column(name = "scheduled_for")
    private LocalDateTime scheduledFor;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}