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
     * Get upload directory path
     */
    @Getter
    private Path uploadPath;

    /**
     * Initialize upload directory on application startup
     */
    @PostConstruct
    public void init() {
        log.info("[FILE-STORAGE-SERVICE] [INIT] Starting file storage service initialization");

        try {
            // Resolve upload directory path
            this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            log.info("[FILE-STORAGE-SERVICE] [INIT] Upload directory configured: {}", uploadPath);

            // Create directory if it doesn't exist
            if (!Files.exists(uploadPath)) {
                log.info("[FILE-STORAGE-SERVICE] [INIT] Creating upload directory: {}", uploadPath);
                Files.createDirectories(uploadPath);
                log.info("[FILE-STORAGE-SERVICE] [INIT] Created upload directory: {}", uploadPath);
            } else {
                log.info("[FILE-STORAGE-SERVICE] [INIT] Upload directory already exists: {}", uploadPath);
            }

            // Create subdirectories
            Path profilesDir = uploadPath.resolve("profiles");
            Path certificatesDir = uploadPath.resolve("certificates");
            Path documentsDir = uploadPath.resolve("documents");

            createDirectoryIfNotExists(profilesDir, "profiles");
            createDirectoryIfNotExists(certificatesDir, "certificates");
            createDirectoryIfNotExists(documentsDir, "documents");

            log.info("[FILE-STORAGE-SERVICE] [INIT] File storage initialized successfully");
            log.info("[FILE-STORAGE-SERVICE] [INIT] Upload directory: {}", uploadPath);
            log.info("[FILE-STORAGE-SERVICE] [INIT] Profiles directory: {}", profilesDir);
            log.info("[FILE-STORAGE-SERVICE] [INIT] Certificates directory: {}", certificatesDir);
            log.info("[FILE-STORAGE-SERVICE] [INIT] Public URL base: {}", publicUrl);

        } catch (IOException e) {
            log.error("[FILE-STORAGE-SERVICE] [INIT] ERROR - Could not initialize upload directory: {}", e.getMessage(), e);
            throw new RuntimeException("Could not initialize upload directory", e);
        }
    }

    private void createDirectoryIfNotExists(Path directory, String directoryName) throws IOException {
        if (!Files.exists(directory)) {
            log.info("[FILE-STORAGE-SERVICE] [INIT] Creating {} directory: {}", directoryName, directory);
            Files.createDirectories(directory);
            log.info("[FILE-STORAGE-SERVICE] [INIT] Created {} directory: {}", directoryName, directory);
        } else {
            log.debug("[FILE-STORAGE-SERVICE] [INIT] {} directory already exists: {}", directoryName, directory);
        }
    }

    /**
     * Store profile picture and return public URL
     */
    public String storeProfilePicture(MultipartFile file, Long studentId) throws IOException {
        log.info("[FILE-STORAGE-SERVICE] [STORE-PROFILE-PICTURE] Started - Student ID: {}", studentId);
        log.debug("[FILE-STORAGE-SERVICE] [STORE-PROFILE-PICTURE] File details - Name: {}, Size: {} bytes",
                file.getOriginalFilename(), file.getSize());

        try {
            // Validate file is not empty
            if (file.isEmpty()) {
                log.error("[FILE-STORAGE-SERVICE] [STORE-PROFILE-PICTURE] ERROR - Empty file provided");
                throw new RuntimeException("Failed to store empty file");
            }

            // Get original filename and extension
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            log.debug("[FILE-STORAGE-SERVICE] [STORE-PROFILE-PICTURE] Original filename: {}, Extension: {}",
                    originalFilename, fileExtension);

            // Generate unique filename
            String filename = "profile_" + studentId + "_" + UUID.randomUUID() + fileExtension;
            log.debug("[FILE-STORAGE-SERVICE] [STORE-PROFILE-PICTURE] Generated filename: {}", filename);

            // Store in profiles subdirectory
            Path targetLocation = uploadPath.resolve("profiles").resolve(filename);
            log.debug("[FILE-STORAGE-SERVICE] [STORE-PROFILE-PICTURE] Target location: {}", targetLocation);

            // Copy file to target location
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            log.info("[FILE-STORAGE-SERVICE] [STORE-PROFILE-PICTURE] File copied successfully to: {}", targetLocation);

            // Return public URL for accessing the file
            String publicUrlPath = publicUrl + "profiles/" + filename;
            log.info("[FILE-STORAGE-SERVICE] [STORE-PROFILE-PICTURE] Completed - Public URL: {}", publicUrlPath);
            return publicUrlPath;

        } catch (Exception e) {
            log.error("[FILE-STORAGE-SERVICE] [STORE-PROFILE-PICTURE] ERROR for student ID {}: {}",
                    studentId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Store achievement certificate and return public URL
     */
    public String storeCertificate(MultipartFile file, Long achievementId) throws IOException {
        log.info("[FILE-STORAGE-SERVICE] [STORE-CERTIFICATE] Started - Achievement ID: {}", achievementId);
        log.debug("[FILE-STORAGE-SERVICE] [STORE-CERTIFICATE] File details - Name: {}, Size: {} bytes",
                file.getOriginalFilename(), file.getSize());

        try {
            // Validate file is not empty
            if (file.isEmpty()) {
                log.error("[FILE-STORAGE-SERVICE] [STORE-CERTIFICATE] ERROR - Empty file provided");
                throw new RuntimeException("Failed to store empty file");
            }

            // Get original filename and extension
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            log.debug("[FILE-STORAGE-SERVICE] [STORE-CERTIFICATE] Original filename: {}, Extension: {}",
                    originalFilename, fileExtension);

            // Generate unique filename
            String filename = "certificate_" + achievementId + "_" + UUID.randomUUID() + fileExtension;
            log.debug("[FILE-STORAGE-SERVICE] [STORE-CERTIFICATE] Generated filename: {}", filename);

            // Store in certificates subdirectory
            Path targetLocation = uploadPath.resolve("certificates").resolve(filename);
            log.debug("[FILE-STORAGE-SERVICE] [STORE-CERTIFICATE] Target location: {}", targetLocation);

            // Copy file to target location
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            log.info("[FILE-STORAGE-SERVICE] [STORE-CERTIFICATE] File copied successfully to: {}", targetLocation);

            // Return public URL for accessing the file
            String publicUrlPath = publicUrl + "certificates/" + filename;
            log.info("[FILE-STORAGE-SERVICE] [STORE-CERTIFICATE] Completed - Public URL: {}", publicUrlPath);
            return publicUrlPath;

        } catch (Exception e) {
            log.error("[FILE-STORAGE-SERVICE] [STORE-CERTIFICATE] ERROR for achievement ID {}: {}",
                    achievementId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Store any document and return public URL
     */
    public String storeDocument(MultipartFile file, String category) throws IOException {
        log.info("[FILE-STORAGE-SERVICE] [STORE-DOCUMENT] Started - Category: {}", category);
        log.debug("[FILE-STORAGE-SERVICE] [STORE-DOCUMENT] File details - Name: {}, Size: {} bytes",
                file.getOriginalFilename(), file.getSize());

        try {
            // Validate file is not empty
            if (file.isEmpty()) {
                log.error("[FILE-STORAGE-SERVICE] [STORE-DOCUMENT] ERROR - Empty file provided");
                throw new RuntimeException("Failed to store empty file");
            }

            // Get original filename and extension
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            log.debug("[FILE-STORAGE-SERVICE] [STORE-DOCUMENT] Original filename: {}, Extension: {}",
                    originalFilename, fileExtension);

            // Generate unique filename
            String filename = category + "_" + UUID.randomUUID() + fileExtension;
            log.debug("[FILE-STORAGE-SERVICE] [STORE-DOCUMENT] Generated filename: {}", filename);

            // Store in documents subdirectory
            Path targetLocation = uploadPath.resolve("documents").resolve(filename);
            log.debug("[FILE-STORAGE-SERVICE] [STORE-DOCUMENT] Target location: {}", targetLocation);

            // Copy file to target location
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            log.info("[FILE-STORAGE-SERVICE] [STORE-DOCUMENT] File copied successfully to: {}", targetLocation);

            // Return public URL for accessing the file
            String publicUrlPath = publicUrl + "documents/" + filename;
            log.info("[FILE-STORAGE-SERVICE] [STORE-DOCUMENT] Completed - Public URL: {}", publicUrlPath);
            return publicUrlPath;

        } catch (Exception e) {
            log.error("[FILE-STORAGE-SERVICE] [STORE-DOCUMENT] ERROR for category {}: {}", category, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String filename) {
        log.trace("[FILE-STORAGE-SERVICE] [GET-FILE-EXTENSION] Getting extension for filename: {}", filename);

        if (filename == null || filename.lastIndexOf(".") == -1) {
            log.warn("[FILE-STORAGE-SERVICE] [GET-FILE-EXTENSION] No extension found, defaulting to .jpg");
            return ".jpg"; // Default extension for images
        }

        String extension = filename.substring(filename.lastIndexOf("."));

        // Ensure extension is valid
        if (extension.length() > 10) { // Sanity check
            log.warn("[FILE-STORAGE-SERVICE] [GET-FILE-EXTENSION] Extension too long ({}), defaulting to .jpg", extension.length());
            return ".jpg";
        }

        String lowerExtension = extension.toLowerCase();
        log.trace("[FILE-STORAGE-SERVICE] [GET-FILE-EXTENSION] Extension: {}", lowerExtension);
        return lowerExtension;
    }

    /**
     * Get profiles directory path
     */
    public Path getProfilesPath() {
        Path profilesPath = uploadPath.resolve("profiles");
        log.trace("[FILE-STORAGE-SERVICE] [GET-PROFILES-PATH] Returning: {}", profilesPath);
        return profilesPath;
    }

    /**
     * Get certificates directory path
     */
    public Path getCertificatesPath() {
        Path certificatesPath = uploadPath.resolve("certificates");
        log.trace("[FILE-STORAGE-SERVICE] [GET-CERTIFICATES-PATH] Returning: {}", certificatesPath);
        return certificatesPath;
    }

    /**
     * Check if a file exists
     */
    public boolean fileExists(String filename, String subdirectory) {
        log.debug("[FILE-STORAGE-SERVICE] [FILE-EXISTS] Checking - Filename: {}, Subdirectory: {}",
                filename, subdirectory);

        Path filePath = uploadPath.resolve(subdirectory).resolve(filename);
        boolean exists = Files.exists(filePath);

        log.debug("[FILE-STORAGE-SERVICE] [FILE-EXISTS] Result: {} - Path: {}", exists, filePath);
        return exists;
    }

    /**
     * Delete a file
     */
    public boolean deleteFile(String filename, String subdirectory) throws IOException {
        log.info("[FILE-STORAGE-SERVICE] [DELETE-FILE] Started - Filename: {}, Subdirectory: {}",
                filename, subdirectory);

        Path filePath = uploadPath.resolve(subdirectory).resolve(filename);
        log.debug("[FILE-STORAGE-SERVICE] [DELETE-FILE] File path: {}", filePath);

        boolean deleted = Files.deleteIfExists(filePath);

        if (deleted) {
            log.info("[FILE-STORAGE-SERVICE] [DELETE-FILE] Completed - File deleted: {}", filePath);
        } else {
            log.warn("[FILE-STORAGE-SERVICE] [DELETE-FILE] File not found: {}", filePath);
        }

        return deleted;
    }
}