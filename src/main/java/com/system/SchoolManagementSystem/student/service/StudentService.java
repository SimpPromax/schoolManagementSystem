package com.system.SchoolManagementSystem.student.service;

import com.system.SchoolManagementSystem.student.dto.*;
import com.system.SchoolManagementSystem.student.entity.*;
import com.system.SchoolManagementSystem.student.repository.*;
import com.system.SchoolManagementSystem.util.FileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentService {

    private final StudentRepository studentRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final AchievementRepository achievementRepository;
    private final StudentInterestRepository studentInterestRepository;
    private final FileStorageService fileStorageService;

    // Base URL for file access
    private static final String BASE_URL = "http://localhost:8080/uploads/";

    // ========== STUDENT METHODS ==========

    /**
     * Get all students
     */
    public List<StudentDTO> getAllStudents() {
        log.info("Fetching all students");
        return studentRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get student by ID
     */
    public StudentDTO getStudentById(Long id) {
        log.info("Fetching student by ID: {}", id);
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + id));
        return convertToDTO(student);
    }

    /**
     * Get student by student ID
     */
    public StudentDTO getStudentByStudentId(String studentId) {
        log.info("Fetching student by student ID: {}", studentId);
        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with student ID: " + studentId));
        return convertToDTO(student);
    }

    /**
     * Get student by email
     */
    public StudentDTO getStudentByEmail(String email) {
        log.info("Fetching student by email: {}", email);
        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Student not found with email: " + email));
        return convertToDTO(student);
    }

    /**
     * Create new student
     */
    @Transactional
    public StudentDTO createStudent(StudentCreateDTO createDTO) {
        log.info("Creating new student: {}", createDTO.getFullName());

        // Check if student ID already exists
        if (studentRepository.existsByStudentId(createDTO.getStudentId())) {
            throw new RuntimeException("Student ID already exists: " + createDTO.getStudentId());
        }

        // Check if email already exists
        if (studentRepository.existsByEmail(createDTO.getEmail())) {
            throw new RuntimeException("Email already exists: " + createDTO.getEmail());
        }

        Student student = convertToEntity(createDTO);
        Student savedStudent = studentRepository.save(student);

        return convertToDTO(savedStudent);
    }

    /**
     * Update student
     */
    @Transactional
    public StudentDTO updateStudent(Long id, StudentUpdateDTO updateDTO) {
        log.info("Updating student with ID: {}", id);

        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + id));

        // Update basic fields
        updateStudentFromDTO(student, updateDTO);

        Student updatedStudent = studentRepository.save(student);
        return convertToDTO(updatedStudent);
    }

    /**
     * Delete student
     */
    @Transactional
    public void deleteStudent(Long id) {
        log.info("Deleting student with ID: {}", id);

        if (!studentRepository.existsById(id)) {
            throw new RuntimeException("Student not found with id: " + id);
        }

        studentRepository.deleteById(id);
    }

    /**
     * Search students by name
     */
    public List<StudentDTO> searchStudentsByName(String name) {
        log.info("Searching students by name: {}", name);
        return studentRepository.searchByName(name).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get students by grade
     */
    public List<StudentDTO> getStudentsByGrade(String grade) {
        log.info("Fetching students by grade: {}", grade);
        return studentRepository.findByGrade(grade).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get students by academic year
     */
    public List<StudentDTO> getStudentsByAcademicYear(String academicYear) {
        log.info("Fetching students by academic year: {}", academicYear);
        return studentRepository.findByAcademicYear(academicYear).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Calculate student age
     */
    public Integer calculateStudentAge(String studentId) {
        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with student ID: " + studentId));

        return Period.between(student.getDateOfBirth(), LocalDate.now()).getYears();
    }

    // ========== FAMILY MEMBER METHODS ==========

    /**
     * Add family member to student
     */
    @Transactional
    public FamilyMemberDTO addFamilyMember(Long studentId, FamilyMemberCreateDTO createDTO) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + studentId));

        FamilyMember familyMember = FamilyMember.builder()
                .student(student)
                .relation(createDTO.getRelation())
                .fullName(createDTO.getFullName())
                .occupation(createDTO.getOccupation())
                .phone(createDTO.getPhone())
                .email(createDTO.getEmail())
                .isPrimaryContact(createDTO.getIsPrimaryContact())
                .isEmergencyContact(createDTO.getIsEmergencyContact())
                .build();

        FamilyMember savedMember = familyMemberRepository.save(familyMember);
        return convertToFamilyMemberDTO(savedMember);
    }

    /**
     * Get family members for student
     */
    public List<FamilyMemberDTO> getFamilyMembers(Long studentId) {
        return familyMemberRepository.findByStudentId(studentId).stream()
                .map(this::convertToFamilyMemberDTO)
                .collect(Collectors.toList());
    }

    /**
     * Update family member
     */
    @Transactional
    public FamilyMemberDTO updateFamilyMember(Long familyMemberId, FamilyMemberCreateDTO updateDTO) {
        FamilyMember familyMember = familyMemberRepository.findById(familyMemberId)
                .orElseThrow(() -> new RuntimeException("Family member not found with id: " + familyMemberId));

        familyMember.setRelation(updateDTO.getRelation());
        familyMember.setFullName(updateDTO.getFullName());
        familyMember.setOccupation(updateDTO.getOccupation());
        familyMember.setPhone(updateDTO.getPhone());
        familyMember.setEmail(updateDTO.getEmail());
        familyMember.setIsPrimaryContact(updateDTO.getIsPrimaryContact());
        familyMember.setIsEmergencyContact(updateDTO.getIsEmergencyContact());

        FamilyMember updatedMember = familyMemberRepository.save(familyMember);
        return convertToFamilyMemberDTO(updatedMember);
    }

    /**
     * Delete family member
     */
    @Transactional
    public void deleteFamilyMember(Long familyMemberId) {
        if (!familyMemberRepository.existsById(familyMemberId)) {
            throw new RuntimeException("Family member not found with id: " + familyMemberId);
        }
        familyMemberRepository.deleteById(familyMemberId);
    }

    // ========== MEDICAL RECORD METHODS ==========

    /**
     * Add medical record to student
     */
    @Transactional
    public MedicalRecordDTO addMedicalRecord(Long studentId, MedicalRecordCreateDTO createDTO) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + studentId));

        MedicalRecord medicalRecord = MedicalRecord.builder()
                .student(student)
                .recordType(createDTO.getRecordType())
                .name(createDTO.getName())
                .severity(createDTO.getSeverity())
                .notes(createDTO.getNotes())
                .frequency(createDTO.getFrequency())
                .prescribedBy(createDTO.getPrescribedBy())
                .build();

        MedicalRecord savedRecord = medicalRecordRepository.save(medicalRecord);
        return convertToMedicalRecordDTO(savedRecord);
    }

    /**
     * Get medical records for student
     */
    public List<MedicalRecordDTO> getMedicalRecords(Long studentId, MedicalRecord.RecordType recordType) {
        if (recordType != null) {
            return medicalRecordRepository.findByStudentIdAndRecordType(studentId, recordType).stream()
                    .map(this::convertToMedicalRecordDTO)
                    .collect(Collectors.toList());
        } else {
            return medicalRecordRepository.findByStudentId(studentId).stream()
                    .map(this::convertToMedicalRecordDTO)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Update medical record
     */
    @Transactional
    public MedicalRecordDTO updateMedicalRecord(Long medicalRecordId, MedicalRecordCreateDTO updateDTO) {
        MedicalRecord medicalRecord = medicalRecordRepository.findById(medicalRecordId)
                .orElseThrow(() -> new RuntimeException("Medical record not found with id: " + medicalRecordId));

        medicalRecord.setRecordType(updateDTO.getRecordType());
        medicalRecord.setName(updateDTO.getName());
        medicalRecord.setSeverity(updateDTO.getSeverity());
        medicalRecord.setNotes(updateDTO.getNotes());
        medicalRecord.setFrequency(updateDTO.getFrequency());
        medicalRecord.setPrescribedBy(updateDTO.getPrescribedBy());

        MedicalRecord updatedRecord = medicalRecordRepository.save(medicalRecord);
        return convertToMedicalRecordDTO(updatedRecord);
    }

    /**
     * Delete medical record
     */
    @Transactional
    public void deleteMedicalRecord(Long medicalRecordId) {
        if (!medicalRecordRepository.existsById(medicalRecordId)) {
            throw new RuntimeException("Medical record not found with id: " + medicalRecordId);
        }
        medicalRecordRepository.deleteById(medicalRecordId);
    }

    // ========== ACHIEVEMENT METHODS ==========

    /**
     * Add achievement to student
     */
    @Transactional
    public AchievementDTO addAchievement(Long studentId, AchievementCreateDTO createDTO) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + studentId));

        Achievement achievement = Achievement.builder()
                .student(student)
                .title(createDTO.getTitle())
                .type(createDTO.getType())
                .level(createDTO.getLevel())
                .year(createDTO.getYear())
                .description(createDTO.getDescription())
                .award(createDTO.getAward())
                .certificatePath(createDTO.getCertificatePath())
                .verifiedBy(createDTO.getVerifiedBy())
                .verifiedAt(createDTO.getVerifiedAt())
                .build();

        Achievement savedAchievement = achievementRepository.save(achievement);
        return convertToAchievementDTO(savedAchievement);
    }

    /**
     * Get achievements for student
     */
    public List<AchievementDTO> getAchievements(Long studentId, Achievement.AchievementType type) {
        if (type != null) {
            return achievementRepository.findByStudentIdAndType(studentId, type).stream()
                    .map(this::convertToAchievementDTO)
                    .collect(Collectors.toList());
        } else {
            return achievementRepository.findByStudentId(studentId).stream()
                    .map(this::convertToAchievementDTO)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Update achievement
     */
    @Transactional
    public AchievementDTO updateAchievement(Long achievementId, AchievementCreateDTO updateDTO) {
        Achievement achievement = achievementRepository.findById(achievementId)
                .orElseThrow(() -> new RuntimeException("Achievement not found with id: " + achievementId));

        achievement.setTitle(updateDTO.getTitle());
        achievement.setType(updateDTO.getType());
        achievement.setLevel(updateDTO.getLevel());
        achievement.setYear(updateDTO.getYear());
        achievement.setDescription(updateDTO.getDescription());
        achievement.setAward(updateDTO.getAward());
        achievement.setCertificatePath(updateDTO.getCertificatePath());
        achievement.setVerifiedBy(updateDTO.getVerifiedBy());
        achievement.setVerifiedAt(updateDTO.getVerifiedAt());

        Achievement updatedAchievement = achievementRepository.save(achievement);
        return convertToAchievementDTO(updatedAchievement);
    }

    /**
     * Delete achievement
     */
    @Transactional
    public void deleteAchievement(Long achievementId) {
        if (!achievementRepository.existsById(achievementId)) {
            throw new RuntimeException("Achievement not found with id: " + achievementId);
        }
        achievementRepository.deleteById(achievementId);
    }

    /**
     * Verify achievement
     */
    @Transactional
    public AchievementDTO verifyAchievement(Long achievementId, String verifiedBy) {
        Achievement achievement = achievementRepository.findById(achievementId)
                .orElseThrow(() -> new RuntimeException("Achievement not found with id: " + achievementId));

        achievement.setVerifiedBy(verifiedBy);
        achievement.setVerifiedAt(java.time.LocalDateTime.now());

        Achievement verifiedAchievement = achievementRepository.save(achievement);
        return convertToAchievementDTO(verifiedAchievement);
    }

    // ========== INTEREST METHODS ==========

    /**
     * Add interest to student
     */
    @Transactional
    public StudentInterestDTO addInterest(Long studentId, StudentInterestCreateDTO createDTO) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + studentId));

        StudentInterest interest = StudentInterest.builder()
                .student(student)
                .interestType(createDTO.getInterestType())
                .name(createDTO.getName())
                .description(createDTO.getDescription())
                .build();

        StudentInterest savedInterest = studentInterestRepository.save(interest);
        return convertToInterestDTO(savedInterest);
    }

    /**
     * Get interests for student
     */
    public List<StudentInterestDTO> getInterests(Long studentId, StudentInterest.InterestType interestType) {
        if (interestType != null) {
            return studentInterestRepository.findByStudentIdAndInterestType(studentId, interestType).stream()
                    .map(this::convertToInterestDTO)
                    .collect(Collectors.toList());
        } else {
            return studentInterestRepository.findByStudentId(studentId).stream()
                    .map(this::convertToInterestDTO)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Update interest
     */
    @Transactional
    public StudentInterestDTO updateInterest(Long interestId, StudentInterestCreateDTO updateDTO) {
        StudentInterest interest = studentInterestRepository.findById(interestId)
                .orElseThrow(() -> new RuntimeException("Interest not found with id: " + interestId));

        interest.setInterestType(updateDTO.getInterestType());
        interest.setName(updateDTO.getName());
        interest.setDescription(updateDTO.getDescription());

        StudentInterest updatedInterest = studentInterestRepository.save(interest);
        return convertToInterestDTO(updatedInterest);
    }

    /**
     * Delete interest
     */
    @Transactional
    public void deleteInterest(Long interestId) {
        if (!studentInterestRepository.existsById(interestId)) {
            throw new RuntimeException("Interest not found with id: " + interestId);
        }
        studentInterestRepository.deleteById(interestId);
    }

    // ========== FILE UPLOAD METHODS ==========

    /**
     * Upload profile picture for student
     */
    @Transactional
    public String uploadProfilePicture(Long studentId, MultipartFile file) throws IOException {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + studentId));

        // Validate file
        FileValidator.validateImageFile(file);

        // Store file and get public URL
        String publicUrl = fileStorageService.storeProfilePicture(file, studentId);

        // Update student profile picture URL in database
        student.setProfilePicture(publicUrl);
        studentRepository.save(student);

        log.info("Profile picture updated for student {}: {}", studentId, publicUrl);

        return publicUrl;
    }

    /**
     * Upload achievement certificate
     */
    @Transactional
    public String uploadAchievementCertificate(Long achievementId, MultipartFile file) throws IOException {
        Achievement achievement = achievementRepository.findById(achievementId)
                .orElseThrow(() -> new RuntimeException("Achievement not found with id: " + achievementId));

        // Validate file
        FileValidator.validateDocumentFile(file);

        // Store file and get public URL
        String publicUrl = fileStorageService.storeCertificate(file, achievementId);

        // Update achievement certificate URL in database
        achievement.setCertificatePath(publicUrl);
        achievementRepository.save(achievement);

        log.info("Certificate uploaded for achievement {}: {}", achievementId, publicUrl);

        return publicUrl;
    }

    /**
     * Get profile picture as Resource
     */
    public Resource getProfilePictureResource(Long studentId) throws IOException {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + studentId));

        if (student.getProfilePicture() == null || student.getProfilePicture().isEmpty()) {
            throw new RuntimeException("Profile picture not found for student: " + studentId);
        }

        // Extract filename from URL
        String filename = student.getProfilePicture().substring(
                student.getProfilePicture().lastIndexOf("/") + 1
        );

        // Get file path
        Path filePath = fileStorageService.getUploadPath()
                .resolve("profiles")
                .resolve(filename);

        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists()) {
            throw new RuntimeException("Profile picture file not found: " + filename);
        }

        return resource;
    }

    // ========== DTO CONVERSION METHODS ==========

    private StudentDTO convertToDTO(Student student) {
        StudentDTO dto = new StudentDTO();

        // Map basic fields
        dto.setId(student.getId());
        dto.setStudentId(student.getStudentId());
        dto.setFullName(student.getFullName());
        dto.setDateOfBirth(student.getDateOfBirth());
        dto.setGender(student.getGender());
        dto.setBloodGroup(student.getBloodGroup());
        dto.setNationality(student.getNationality());
        dto.setReligion(student.getReligion());
        dto.setCategory(student.getCategory());
        dto.setProfilePicture(student.getProfilePicture());
        dto.setAdmissionDate(student.getAdmissionDate());
        dto.setAcademicYear(student.getAcademicYear());
        dto.setGrade(student.getGrade());
        dto.setRollNumber(student.getRollNumber());
        dto.setClassTeacher(student.getClassTeacher());
        dto.setHouse(student.getHouse());

        // Contact
        dto.setAddress(student.getAddress());
        dto.setPhone(student.getPhone());
        dto.setEmail(student.getEmail());
        dto.setEmergencyContactName(student.getEmergencyContactName());
        dto.setEmergencyContactPhone(student.getEmergencyContactPhone());
        dto.setEmergencyRelation(student.getEmergencyRelation());

        // Medical
        dto.setHeight(student.getHeight());
        dto.setWeight(student.getWeight());
        dto.setBloodPressure(student.getBloodPressure());
        dto.setLastMedicalCheckup(student.getLastMedicalCheckup());
        dto.setDoctorName(student.getDoctorName());
        dto.setClinicName(student.getClinicName());

        // Transport
        dto.setTransportMode(student.getTransportMode());
        dto.setBusRoute(student.getBusRoute());
        dto.setBusStop(student.getBusStop());
        dto.setBusNumber(student.getBusNumber());
        dto.setDriverName(student.getDriverName());
        dto.setDriverContact(student.getDriverContact());
        dto.setPickupTime(student.getPickupTime());
        dto.setDropTime(student.getDropTime());
        dto.setTransportFee(student.getTransportFee());
        dto.setTransportFeeStatus(student.getTransportFeeStatus());

        // Map related entities
        List<FamilyMemberDTO> familyMemberDTOs = student.getFamilyMembers().stream()
                .map(this::convertToFamilyMemberDTO)
                .collect(Collectors.toList());
        dto.setFamilyMembers(familyMemberDTOs);

        List<MedicalRecordDTO> medicalRecordDTOs = student.getMedicalRecords().stream()
                .map(this::convertToMedicalRecordDTO)
                .collect(Collectors.toList());
        dto.setMedicalRecords(medicalRecordDTOs);

        List<AchievementDTO> achievementDTOs = student.getAchievements().stream()
                .map(this::convertToAchievementDTO)
                .collect(Collectors.toList());
        dto.setAchievements(achievementDTOs);

        // Separate clubs and hobbies
        List<StudentInterestDTO> interestDTOs = student.getInterests().stream()
                .map(this::convertToInterestDTO)
                .collect(Collectors.toList());

        dto.setClubs(interestDTOs.stream()
                .filter(i -> i.getInterestType() == StudentInterest.InterestType.CLUB)
                .map(StudentInterestDTO::getName)
                .collect(Collectors.toList()));

        dto.setHobbies(interestDTOs.stream()
                .filter(i -> i.getInterestType() == StudentInterest.InterestType.HOBBY)
                .map(StudentInterestDTO::getName)
                .collect(Collectors.toList()));

        return dto;
    }

    private FamilyMemberDTO convertToFamilyMemberDTO(FamilyMember familyMember) {
        FamilyMemberDTO dto = new FamilyMemberDTO();
        dto.setId(familyMember.getId());
        dto.setRelation(familyMember.getRelation());
        dto.setFullName(familyMember.getFullName());
        dto.setOccupation(familyMember.getOccupation());
        dto.setPhone(familyMember.getPhone());
        dto.setEmail(familyMember.getEmail());
        dto.setIsPrimaryContact(familyMember.getIsPrimaryContact());
        dto.setIsEmergencyContact(familyMember.getIsEmergencyContact());
        return dto;
    }

    private MedicalRecordDTO convertToMedicalRecordDTO(MedicalRecord medicalRecord) {
        MedicalRecordDTO dto = new MedicalRecordDTO();
        dto.setId(medicalRecord.getId());
        dto.setRecordType(medicalRecord.getRecordType());
        dto.setName(medicalRecord.getName());
        dto.setSeverity(medicalRecord.getSeverity());
        dto.setNotes(medicalRecord.getNotes());
        dto.setFrequency(medicalRecord.getFrequency());
        dto.setPrescribedBy(medicalRecord.getPrescribedBy());
        return dto;
    }

    private AchievementDTO convertToAchievementDTO(Achievement achievement) {
        AchievementDTO dto = new AchievementDTO();
        dto.setId(achievement.getId());
        dto.setTitle(achievement.getTitle());
        dto.setType(achievement.getType());
        dto.setLevel(achievement.getLevel());
        dto.setYear(achievement.getYear());
        dto.setDescription(achievement.getDescription());
        dto.setAward(achievement.getAward());
        dto.setCertificatePath(achievement.getCertificatePath());
        dto.setVerified(achievement.getVerifiedBy() != null);
        return dto;
    }

    private StudentInterestDTO convertToInterestDTO(StudentInterest interest) {
        StudentInterestDTO dto = new StudentInterestDTO();
        dto.setId(interest.getId());
        dto.setInterestType(interest.getInterestType());
        dto.setName(interest.getName());
        dto.setDescription(interest.getDescription());
        return dto;
    }

    private Student convertToEntity(StudentCreateDTO createDTO) {
        return Student.builder()
                .studentId(createDTO.getStudentId())
                .fullName(createDTO.getFullName())
                .dateOfBirth(createDTO.getDateOfBirth())
                .gender(createDTO.getGender())
                .bloodGroup(createDTO.getBloodGroup())
                .nationality(createDTO.getNationality())
                .religion(createDTO.getReligion())
                .category(createDTO.getCategory())
                .admissionDate(createDTO.getAdmissionDate())
                .academicYear(createDTO.getAcademicYear())
                .grade(createDTO.getGrade())
                .rollNumber(createDTO.getRollNumber())
                .classTeacher(createDTO.getClassTeacher())
                .house(createDTO.getHouse())
                .address(createDTO.getAddress())
                .phone(createDTO.getPhone())
                .email(createDTO.getEmail())
                .emergencyContactName(createDTO.getEmergencyContactName())
                .emergencyContactPhone(createDTO.getEmergencyContactPhone())
                .emergencyRelation(createDTO.getEmergencyRelation())
                .build();
    }

    private void updateStudentFromDTO(Student student, StudentUpdateDTO updateDTO) {
        if (updateDTO.getFullName() != null) student.setFullName(updateDTO.getFullName());
        if (updateDTO.getDateOfBirth() != null) student.setDateOfBirth(updateDTO.getDateOfBirth());
        if (updateDTO.getGender() != null) student.setGender(updateDTO.getGender());
        if (updateDTO.getBloodGroup() != null) student.setBloodGroup(updateDTO.getBloodGroup());
        if (updateDTO.getNationality() != null) student.setNationality(updateDTO.getNationality());
        if (updateDTO.getReligion() != null) student.setReligion(updateDTO.getReligion());
        if (updateDTO.getCategory() != null) student.setCategory(updateDTO.getCategory());
        if (updateDTO.getProfilePicture() != null) student.setProfilePicture(updateDTO.getProfilePicture());
        if (updateDTO.getGrade() != null) student.setGrade(updateDTO.getGrade());
        if (updateDTO.getRollNumber() != null) student.setRollNumber(updateDTO.getRollNumber());
        if (updateDTO.getClassTeacher() != null) student.setClassTeacher(updateDTO.getClassTeacher());
        if (updateDTO.getHouse() != null) student.setHouse(updateDTO.getHouse());
        if (updateDTO.getAddress() != null) student.setAddress(updateDTO.getAddress());
        if (updateDTO.getPhone() != null) student.setPhone(updateDTO.getPhone());
        if (updateDTO.getEmail() != null) student.setEmail(updateDTO.getEmail());
        if (updateDTO.getEmergencyContactName() != null) student.setEmergencyContactName(updateDTO.getEmergencyContactName());
        if (updateDTO.getEmergencyContactPhone() != null) student.setEmergencyContactPhone(updateDTO.getEmergencyContactPhone());
        if (updateDTO.getEmergencyRelation() != null) student.setEmergencyRelation(updateDTO.getEmergencyRelation());
        if (updateDTO.getHeight() != null) student.setHeight(updateDTO.getHeight());
        if (updateDTO.getWeight() != null) student.setWeight(updateDTO.getWeight());
        if (updateDTO.getBloodPressure() != null) student.setBloodPressure(updateDTO.getBloodPressure());
        if (updateDTO.getLastMedicalCheckup() != null) student.setLastMedicalCheckup(updateDTO.getLastMedicalCheckup());
        if (updateDTO.getDoctorName() != null) student.setDoctorName(updateDTO.getDoctorName());
        if (updateDTO.getClinicName() != null) student.setClinicName(updateDTO.getClinicName());
        if (updateDTO.getTransportMode() != null) student.setTransportMode(updateDTO.getTransportMode());
        if (updateDTO.getBusRoute() != null) student.setBusRoute(updateDTO.getBusRoute());
        if (updateDTO.getBusStop() != null) student.setBusStop(updateDTO.getBusStop());
        if (updateDTO.getBusNumber() != null) student.setBusNumber(updateDTO.getBusNumber());
        if (updateDTO.getDriverName() != null) student.setDriverName(updateDTO.getDriverName());
        if (updateDTO.getDriverContact() != null) student.setDriverContact(updateDTO.getDriverContact());
        if (updateDTO.getPickupTime() != null) student.setPickupTime(updateDTO.getPickupTime());
        if (updateDTO.getDropTime() != null) student.setDropTime(updateDTO.getDropTime());
    }
}