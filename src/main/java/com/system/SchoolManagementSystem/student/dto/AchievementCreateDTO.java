package com.system.SchoolManagementSystem.student.dto;

import com.system.SchoolManagementSystem.student.entity.Achievement;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AchievementCreateDTO {
    private String title;
    private Achievement.AchievementType type;
    private Achievement.AchievementLevel level;
    private Integer year;
    private String description;
    private String award;
    private String certificatePath;
    private String verifiedBy;
    private LocalDateTime verifiedAt;
}