package com.system.SchoolManagementSystem.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoResponse {
    private Long id;
    private String name;
    private String description;
    private Integer value;
    private String category;
    private Boolean isActive;
    private String tenantId;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}