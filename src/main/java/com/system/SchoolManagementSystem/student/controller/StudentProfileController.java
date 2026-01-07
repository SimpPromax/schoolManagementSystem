package com.system.SchoolManagementSystem.student.controller;

import com.system.SchoolManagementSystem.student.dto.*;
import com.system.SchoolManagementSystem.student.entity.*;
import com.system.SchoolManagementSystem.student.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
@Slf4j
public class StudentProfileController {

    private final StudentService studentService;
    private static final String UPLOAD_DIR = System.getProperty("user.home") + "/school-management-uploads/";

    // ========== STUDENT CRUD OPERATIONS ==========

    /**
     * Get all students
     */
    @GetMapping
    public ResponseEntity<List<StudentDTO>> getAllStudents() {
        log.info("GET /api/v1/students - Fetching all students");
        List<StudentDTO> students = studentService.getAllStudents();
        return ResponseEntity.ok(students);
    }

    /**
     * Get student by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<StudentDTO> getStudentById(@PathVariable Long id) {
        log.info("GET /api/v1/students/{} - Fetching student by ID", id);
        StudentDTO student = studentService.getStudentById(id);
        return ResponseEntity.ok(student);
    }

    /**
     * Get student by student ID
     */
    @GetMapping("/by-student-id/{studentId}")
    public ResponseEntity<StudentDTO> getStudentByStudentId(@PathVariable String studentId) {
        log.info("GET /api/v1/students/by-student-id/{} - Fetching student by student ID", studentId);
        StudentDTO student = studentService.getStudentByStudentId(studentId);
        return ResponseEntity.ok(student);
    }

    /**
     * Get student by email
     */
    @GetMapping("/by-email/{email}")
    public ResponseEntity<StudentDTO> getStudentByEmail(@PathVariable String email) {
        log.info("GET /api/v1/students/by-email/{} - Fetching student by email", email);
        StudentDTO student = studentService.getStudentByEmail(email);
        return ResponseEntity.ok(student);
    }

    /**
     * Create new student
     */
    @PostMapping
    public ResponseEntity<StudentDTO> createStudent(@Valid @RequestBody StudentCreateDTO createDTO) {
        log.info("POST /api/v1/students - Creating new student: {}", createDTO.getFullName());
        StudentDTO createdStudent = studentService.createStudent(createDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdStudent);
    }

    /**
     * Update student
     */
    @PutMapping("/{id}")
    public ResponseEntity<StudentDTO> updateStudent(
            @PathVariable Long id,
            @Valid @RequestBody StudentUpdateDTO updateDTO) {
        log.info("PUT /api/v1/students/{} - Updating student", id);
        StudentDTO updatedStudent = studentService.updateStudent(id, updateDTO);
        return ResponseEntity.ok(updatedStudent);
    }

    /**
     * Delete student
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudent(@PathVariable Long id) {
        log.info("DELETE /api/v1/students/{} - Deleting student", id);
        studentService.deleteStudent(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Search students by name
     */
    @GetMapping("/search")
    public ResponseEntity<List<StudentDTO>> searchStudents(@RequestParam String name) {
        log.info("GET /api/v1/students/search?name={} - Searching students", name);
        List<StudentDTO> students = studentService.searchStudentsByName(name);
        return ResponseEntity.ok(students);
    }

    /**
     * Get students by grade
     */
    @GetMapping("/by-grade/{grade}")
    public ResponseEntity<List<StudentDTO>> getStudentsByGrade(@PathVariable String grade) {
        log.info("GET /api/v1/students/by-grade/{} - Fetching students by grade", grade);
        List<StudentDTO> students = studentService.getStudentsByGrade(grade);
        return ResponseEntity.ok(students);
    }

    /**
     * Get students by academic year
     */
    @GetMapping("/by-academic-year/{academicYear}")
    public ResponseEntity<List<StudentDTO>> getStudentsByAcademicYear(@PathVariable String academicYear) {
        log.info("GET /api/v1/students/by-academic-year/{} - Fetching students by academic year", academicYear);
        List<StudentDTO> students = studentService.getStudentsByAcademicYear(academicYear);
        return ResponseEntity.ok(students);
    }

    /**
     * Get student age
     */
    @GetMapping("/{studentId}/age")
    public ResponseEntity<Map<String, Integer>> getStudentAge(@PathVariable String studentId) {
        log.info("GET /api/v1/students/{}/age - Calculating student age", studentId);
        Integer age = studentService.calculateStudentAge(studentId);
        Map<String, Integer> response = new HashMap<>();
        response.put("age", age);
        return ResponseEntity.ok(response);
    }

    // ========== FAMILY MEMBER OPERATIONS ==========

    /**
     * Get family members for student
     */
    @GetMapping("/{studentId}/family-members")
    public ResponseEntity<List<FamilyMemberDTO>> getFamilyMembers(@PathVariable Long studentId) {
        log.info("GET /api/v1/students/{}/family-members - Fetching family members", studentId);
        List<FamilyMemberDTO> familyMembers = studentService.getFamilyMembers(studentId);
        return ResponseEntity.ok(familyMembers);
    }

    /**
     * Add family member to student
     */
    @PostMapping("/{studentId}/family-members")
    public ResponseEntity<FamilyMemberDTO> addFamilyMember(
            @PathVariable Long studentId,
            @Valid @RequestBody FamilyMemberCreateDTO createDTO) {
        log.info("POST /api/v1/students/{}/family-members - Adding family member", studentId);
        FamilyMemberDTO familyMember = studentService.addFamilyMember(studentId, createDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(familyMember);
    }

    /**
     * Update family member
     */
    @PutMapping("/family-members/{familyMemberId}")
    public ResponseEntity<FamilyMemberDTO> updateFamilyMember(
            @PathVariable Long familyMemberId,
            @Valid @RequestBody FamilyMemberCreateDTO updateDTO) {
        log.info("PUT /api/v1/students/family-members/{} - Updating family member", familyMemberId);
        FamilyMemberDTO updatedMember = studentService.updateFamilyMember(familyMemberId, updateDTO);
        return ResponseEntity.ok(updatedMember);
    }

    /**
     * Delete family member
     */
    @DeleteMapping("/family-members/{familyMemberId}")
    public ResponseEntity<Void> deleteFamilyMember(@PathVariable Long familyMemberId) {
        log.info("DELETE /api/v1/students/family-members/{} - Deleting family member", familyMemberId);
        studentService.deleteFamilyMember(familyMemberId);
        return ResponseEntity.noContent().build();
    }

    // ========== MEDICAL RECORD OPERATIONS ==========

    /**
     * Get medical records for student
     */
    @GetMapping("/{studentId}/medical-records")
    public ResponseEntity<List<MedicalRecordDTO>> getMedicalRecords(
            @PathVariable Long studentId,
            @RequestParam(required = false) MedicalRecord.RecordType recordType) {
        log.info("GET /api/v1/students/{}/medical-records - Fetching medical records", studentId);
        List<MedicalRecordDTO> medicalRecords = studentService.getMedicalRecords(studentId, recordType);
        return ResponseEntity.ok(medicalRecords);
    }

    /**
     * Add medical record to student
     */
    @PostMapping("/{studentId}/medical-records")
    public ResponseEntity<MedicalRecordDTO> addMedicalRecord(
            @PathVariable Long studentId,
            @Valid @RequestBody MedicalRecordCreateDTO createDTO) {
        log.info("POST /api/v1/students/{}/medical-records - Adding medical record", studentId);
        MedicalRecordDTO medicalRecord = studentService.addMedicalRecord(studentId, createDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(medicalRecord);
    }

    /**
     * Update medical record
     */
    @PutMapping("/medical-records/{medicalRecordId}")
    public ResponseEntity<MedicalRecordDTO> updateMedicalRecord(
            @PathVariable Long medicalRecordId,
            @Valid @RequestBody MedicalRecordCreateDTO updateDTO) {
        log.info("PUT /api/v1/students/medical-records/{} - Updating medical record", medicalRecordId);
        MedicalRecordDTO updatedRecord = studentService.updateMedicalRecord(medicalRecordId, updateDTO);
        return ResponseEntity.ok(updatedRecord);
    }

    /**
     * Delete medical record
     */
    @DeleteMapping("/medical-records/{medicalRecordId}")
    public ResponseEntity<Void> deleteMedicalRecord(@PathVariable Long medicalRecordId) {
        log.info("DELETE /api/v1/students/medical-records/{} - Deleting medical record", medicalRecordId);
        studentService.deleteMedicalRecord(medicalRecordId);
        return ResponseEntity.noContent().build();
    }

    // ========== ACHIEVEMENT OPERATIONS ==========

    /**
     * Get achievements for student
     */
    @GetMapping("/{studentId}/achievements")
    public ResponseEntity<List<AchievementDTO>> getAchievements(
            @PathVariable Long studentId,
            @RequestParam(required = false) Achievement.AchievementType type) {
        log.info("GET /api/v1/students/{}/achievements - Fetching achievements", studentId);
        List<AchievementDTO> achievements = studentService.getAchievements(studentId, type);
        return ResponseEntity.ok(achievements);
    }

    /**
     * Add achievement to student
     */
    @PostMapping("/{studentId}/achievements")
    public ResponseEntity<AchievementDTO> addAchievement(
            @PathVariable Long studentId,
            @Valid @RequestBody AchievementCreateDTO createDTO) {
        log.info("POST /api/v1/students/{}/achievements - Adding achievement", studentId);
        AchievementDTO achievement = studentService.addAchievement(studentId, createDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(achievement);
    }

    /**
     * Update achievement
     */
    @PutMapping("/achievements/{achievementId}")
    public ResponseEntity<AchievementDTO> updateAchievement(
            @PathVariable Long achievementId,
            @Valid @RequestBody AchievementCreateDTO updateDTO) {
        log.info("PUT /api/v1/students/achievements/{} - Updating achievement", achievementId);
        AchievementDTO updatedAchievement = studentService.updateAchievement(achievementId, updateDTO);
        return ResponseEntity.ok(updatedAchievement);
    }

    /**
     * Delete achievement
     */
    @DeleteMapping("/achievements/{achievementId}")
    public ResponseEntity<Void> deleteAchievement(@PathVariable Long achievementId) {
        log.info("DELETE /api/v1/students/achievements/{} - Deleting achievement", achievementId);
        studentService.deleteAchievement(achievementId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Verify achievement
     */
    @PutMapping("/achievements/{achievementId}/verify")
    public ResponseEntity<AchievementDTO> verifyAchievement(
            @PathVariable Long achievementId,
            @RequestParam String verifiedBy) {
        log.info("PUT /api/v1/students/achievements/{}/verify - Verifying achievement", achievementId);
        AchievementDTO achievement = studentService.verifyAchievement(achievementId, verifiedBy);
        return ResponseEntity.ok(achievement);
    }

    // ========== INTEREST OPERATIONS ==========

    /**
     * Get interests for student
     */
    @GetMapping("/{studentId}/interests")
    public ResponseEntity<List<StudentInterestDTO>> getInterests(
            @PathVariable Long studentId,
            @RequestParam(required = false) StudentInterest.InterestType interestType) {
        log.info("GET /api/v1/students/{}/interests - Fetching interests", studentId);
        List<StudentInterestDTO> interests = studentService.getInterests(studentId, interestType);
        return ResponseEntity.ok(interests);
    }

    /**
     * Add interest to student
     */
    @PostMapping("/{studentId}/interests")
    public ResponseEntity<StudentInterestDTO> addInterest(
            @PathVariable Long studentId,
            @Valid @RequestBody StudentInterestCreateDTO createDTO) {
        log.info("POST /api/v1/students/{}/interests - Adding interest", studentId);
        StudentInterestDTO interest = studentService.addInterest(studentId, createDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(interest);
    }

    /**
     * Update interest
     */
    @PutMapping("/interests/{interestId}")
    public ResponseEntity<StudentInterestDTO> updateInterest(
            @PathVariable Long interestId,
            @Valid @RequestBody StudentInterestCreateDTO updateDTO) {
        log.info("PUT /api/v1/students/interests/{} - Updating interest", interestId);
        StudentInterestDTO updatedInterest = studentService.updateInterest(interestId, updateDTO);
        return ResponseEntity.ok(updatedInterest);
    }

    /**
     * Delete interest
     */
    @DeleteMapping("/interests/{interestId}")
    public ResponseEntity<Void> deleteInterest(@PathVariable Long interestId) {
        log.info("DELETE /api/v1/students/interests/{} - Deleting interest", interestId);
        studentService.deleteInterest(interestId);
        return ResponseEntity.noContent().build();
    }

    // ========== FILE UPLOAD OPERATIONS ==========

    /**
     * Upload profile picture for student
     */
    @PostMapping(value = "/{studentId}/upload-profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadProfilePicture(
            @PathVariable Long studentId,
            @RequestParam("file") MultipartFile file) {
        log.info("POST /api/v1/students/{}/upload-profile-picture - Uploading profile picture", studentId);

        try {
            String fileUrl = studentService.uploadProfilePicture(studentId, file);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Profile picture uploaded successfully");
            response.put("fileUrl", fileUrl);
            response.put("profilePicture", fileUrl);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error uploading profile picture: {}", e.getMessage());
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to upload profile picture: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Upload achievement certificate
     */
    @PostMapping(value = "/achievements/{achievementId}/upload-certificate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadAchievementCertificate(
            @PathVariable Long achievementId,
            @RequestParam("file") MultipartFile file) {
        log.info("POST /api/v1/students/achievements/{}/upload-certificate - Uploading certificate", achievementId);

        try {
            String fileUrl = studentService.uploadAchievementCertificate(achievementId, file);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Certificate uploaded successfully");
            response.put("fileUrl", fileUrl);
            response.put("certificatePath", fileUrl);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error uploading certificate: {}", e.getMessage());
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to upload certificate: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get student's profile picture
     */
    @GetMapping("/{studentId}/profile-picture")
    public ResponseEntity<Resource> getProfilePicture(@PathVariable Long studentId) {
        try {
            Resource resource = studentService.getProfilePictureResource(studentId);

            if (resource.exists() && resource.isReadable()) {
                String contentType = determineContentType(resource);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error retrieving profile picture: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String determineContentType(Resource resource) {
        try {
            String filename = resource.getFilename();
            if (filename != null) {
                if (filename.toLowerCase().endsWith(".png")) {
                    return "image/png";
                } else if (filename.toLowerCase().endsWith(".gif")) {
                    return "image/gif";
                }
            }
            return "image/jpeg"; // Default
        } catch (Exception e) {
            return "image/jpeg";
        }
    }

    // ========== DEMO ENDPOINTS ==========

    /**
     * Demo endpoint to get complete profile
     */
    @GetMapping("/demo/complete-profile")
    public ResponseEntity<StudentDTO> getDemoCompleteProfile() {
        log.info("GET /api/v1/students/demo/complete-profile - Getting demo profile");

        // Create demo student DTO
        StudentDTO demoStudent = new StudentDTO();
        demoStudent.setId(1L);
        demoStudent.setStudentId("STU20240025");
        demoStudent.setFullName("John Smith");
        demoStudent.setDateOfBirth(java.time.LocalDate.of(2009, 6, 15));
        demoStudent.setGender(Student.Gender.MALE);
        demoStudent.setBloodGroup(Student.BloodGroup.O_PLUS);
        demoStudent.setNationality("Indian");
        demoStudent.setReligion("Christian");
        demoStudent.setCategory(Student.Category.GENERAL);
        demoStudent.setGrade("10-A");
        demoStudent.setRollNumber("25");
        demoStudent.setAcademicYear("2024-25");
        demoStudent.setClassTeacher("Mrs. Johnson");
        demoStudent.setHouse("Blue House");
        demoStudent.setAdmissionDate(java.time.LocalDate.of(2020, 6, 1));
        demoStudent.setAddress("123 Maple Street, Springfield, 560001");
        demoStudent.setPhone("+91 9876543210");
        demoStudent.setEmail("john.smith@springfield.edu");
        demoStudent.setEmergencyContactName("Robert Smith");
        demoStudent.setEmergencyContactPhone("+91 9876543211");
        demoStudent.setEmergencyRelation("Father");
        demoStudent.setProfilePicture("http://localhost:8080/uploads/profiles/demo_profile.jpg");

        // Add demo data for frontend testing
        demoStudent.setClubs(List.of("Football Club", "Science Club", "Debate Society"));
        demoStudent.setHobbies(List.of("Reading", "Football", "Painting", "Coding"));

        // Demo family members
        FamilyMemberDTO father = new FamilyMemberDTO();
        father.setId(1L);
        father.setRelation(FamilyMember.Relation.FATHER);
        father.setFullName("Robert Smith");
        father.setOccupation("Engineer");
        father.setPhone("+91 9876543211");
        father.setEmail("robert.smith@email.com");
        father.setIsPrimaryContact(true);
        father.setIsEmergencyContact(true);

        FamilyMemberDTO mother = new FamilyMemberDTO();
        mother.setId(2L);
        mother.setRelation(FamilyMember.Relation.MOTHER);
        mother.setFullName("Mary Smith");
        mother.setOccupation("Teacher");
        mother.setPhone("+91 9876543212");
        mother.setEmail("mary.smith@email.com");
        mother.setIsPrimaryContact(false);
        mother.setIsEmergencyContact(true);

        demoStudent.setFamilyMembers(List.of(father, mother));

        // Demo medical records
        MedicalRecordDTO allergy = new MedicalRecordDTO();
        allergy.setId(1L);
        allergy.setRecordType(MedicalRecord.RecordType.ALLERGY);
        allergy.setName("Peanuts");
        allergy.setSeverity(MedicalRecord.Severity.SEVERE);
        allergy.setNotes("Causes breathing difficulties");

        demoStudent.setMedicalRecords(List.of(allergy));

        // Demo achievements
        AchievementDTO achievement = new AchievementDTO();
        achievement.setId(1L);
        achievement.setTitle("Science Fair Winner");
        achievement.setType(Achievement.AchievementType.ACADEMIC);
        achievement.setLevel(Achievement.AchievementLevel.SCHOOL);
        achievement.setYear(2024);
        achievement.setDescription("Won first prize in school science fair");
        achievement.setAward("Gold Medal");
        achievement.setVerified(true);

        demoStudent.setAchievements(List.of(achievement));

        return ResponseEntity.ok(demoStudent);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Student Profile Service");
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    // ========== ERROR HANDLING ==========

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        log.error("RuntimeException occurred: {}", ex.getMessage(), ex);
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", ex.getMessage());
        errorResponse.put("timestamp", java.time.LocalDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        log.error("Exception occurred: {}", ex.getMessage(), ex);
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "An unexpected error occurred");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("timestamp", java.time.LocalDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}