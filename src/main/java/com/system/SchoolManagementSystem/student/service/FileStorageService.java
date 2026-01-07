package com.system.SchoolManagementSystem.student.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.public-url}")
    private String publicUrl;

    /**
     * -- GETTER --
     *  Get upload directory path
     */
    @Getter
    private Path uploadPath;

    /**
     * Initialize upload directory on application startup
     */
    @PostConstruct
    public void init() {
        try {
            // Resolve upload directory path
            this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

            // Create directory if it doesn't exist
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("Created upload directory: {}", uploadPath);
            }

            // Create subdirectories
            Path profilesDir = uploadPath.resolve("profiles");
            Path certificatesDir = uploadPath.resolve("certificates");
            Path documentsDir = uploadPath.resolve("documents");

            Files.createDirectories(profilesDir);
            Files.createDirectories(certificatesDir);
            Files.createDirectories(documentsDir);

            log.info("File storage initialized successfully");
            log.info("Upload directory: {}", uploadPath);
            log.info("Profiles directory: {}", profilesDir);
            log.info("Certificates directory: {}", certificatesDir);
            log.info("Public URL base: {}", publicUrl);

        } catch (IOException e) {
            log.error("Could not initialize upload directory: {}", e.getMessage());
            throw new RuntimeException("Could not initialize upload directory", e);
        }
    }

    /**
     * Store profile picture and return public URL
     */
    public String storeProfilePicture(MultipartFile file, Long studentId) throws IOException {
        // Validate file is not empty
        if (file.isEmpty()) {
            throw new RuntimeException("Failed to store empty file");
        }

        // Get original filename and extension
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);

        // Generate unique filename
        String filename = "profile_" + studentId + "_" + UUID.randomUUID() + fileExtension;

        // Store in profiles subdirectory
        Path targetLocation = uploadPath.resolve("profiles").resolve(filename);

        // Copy file to target location
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        log.info("Profile picture stored: {} for student {}", targetLocation, studentId);

        // Return public URL for accessing the file
        return publicUrl + "profiles/" + filename;
    }

    /**
     * Store achievement certificate and return public URL
     */
    public String storeCertificate(MultipartFile file, Long achievementId) throws IOException {
        // Validate file is not empty
        if (file.isEmpty()) {
            throw new RuntimeException("Failed to store empty file");
        }

        // Get original filename and extension
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);

        // Generate unique filename
        String filename = "certificate_" + achievementId + "_" + UUID.randomUUID() + fileExtension;

        // Store in certificates subdirectory
        Path targetLocation = uploadPath.resolve("certificates").resolve(filename);

        // Copy file to target location
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        log.info("Certificate stored: {} for achievement {}", targetLocation, achievementId);

        // Return public URL for accessing the file
        return publicUrl + "certificates/" + filename;
    }

    /**
     * Store any document and return public URL
     */
    public String storeDocument(MultipartFile file, String category) throws IOException {
        // Validate file is not empty
        if (file.isEmpty()) {
            throw new RuntimeException("Failed to store empty file");
        }

        // Get original filename and extension
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);

        // Generate unique filename
        String filename = category + "_" + UUID.randomUUID() + fileExtension;

        // Store in documents subdirectory
        Path targetLocation = uploadPath.resolve("documents").resolve(filename);

        // Copy file to target location
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        log.info("Document stored: {} in category {}", targetLocation, category);

        // Return public URL for accessing the file
        return publicUrl + "documents/" + filename;
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return ".jpg"; // Default extension for images
        }
        String extension = filename.substring(filename.lastIndexOf("."));
        // Ensure extension is valid
        if (extension.length() > 10) { // Sanity check
            return ".jpg";
        }
        return extension.toLowerCase();
    }

    /**
     * Get profiles directory path
     */
    public Path getProfilesPath() {
        return uploadPath.resolve("profiles");
    }

    /**
     * Get certificates directory path
     */
    public Path getCertificatesPath() {
        return uploadPath.resolve("certificates");
    }

    /**
     * Check if a file exists
     */
    public boolean fileExists(String filename, String subdirectory) {
        Path filePath = uploadPath.resolve(subdirectory).resolve(filename);
        return Files.exists(filePath);
    }

    /**
     * Delete a file
     */
    public boolean deleteFile(String filename, String subdirectory) throws IOException {
        Path filePath = uploadPath.resolve(subdirectory).resolve(filename);
        return Files.deleteIfExists(filePath);
    }
}