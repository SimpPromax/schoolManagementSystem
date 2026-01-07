package com.system.SchoolManagementSystem.student.dto;

import com.system.SchoolManagementSystem.student.entity.FamilyMember;
import lombok.Data;

@Data
public class FamilyMemberDTO {
    private Long id;
    private FamilyMember.Relation relation;
    private String fullName;
    private String occupation;
    private String phone;
    private String email;
    private Boolean isPrimaryContact;
    private Boolean isEmergencyContact;
}