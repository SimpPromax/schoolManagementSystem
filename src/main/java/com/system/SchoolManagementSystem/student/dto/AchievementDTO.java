package com.system.SchoolManagementSystem.student.dto;

import com.system.SchoolManagementSystem.student.entity.Achievement;
import lombok.Data;

@Data
public class AchievementDTO {
    private Long id;
    private String title;
    private Achievement.AchievementType type;
    private Achievement.AchievementLevel level;
    private Integer year;
    private String description;
    private String award;
    private String certificatePath;
    private Boolean verified;
}