package com.system.SchoolManagementSystem.util;

import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

public class FileValidator {

    // Allowed image formats
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    // Allowed document formats
    private static final List<String> ALLOWED_DOCUMENT_TYPES = Arrays.asList(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    // Maximum file size in bytes (10MB)
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /**
     * Validate image file
     */
    public static void validateImageFile(MultipartFile file) {
        logValidationStart("image", file);

        if (file == null || file.isEmpty()) {
            logValidationError("File is empty or null");
            throw new RuntimeException("File is empty or null");
        }

        long fileSize = file.getSize();
        if (fileSize > MAX_FILE_SIZE) {
            logValidationError("File size exceeds 10MB limit. Size: " + fileSize + " bytes");
            throw new RuntimeException("File size exceeds 10MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            logValidationError("Invalid file type. Only JPG, PNG, GIF, WEBP are allowed. Got: " + contentType);
            throw new RuntimeException(
                    "Invalid file type. Only JPG, PNG, GIF, WEBP are allowed. Got: " + contentType
            );
        }

        // Validate filename
        String filename = file.getOriginalFilename();
        if (filename == null || filename.contains("..")) {
            logValidationError("Invalid filename: " + filename);
            throw new RuntimeException("Invalid filename");
        }

        logValidationSuccess("image", file);
    }

    /**
     * Validate document file
     */
    public static void validateDocumentFile(MultipartFile file) {
        logValidationStart("document", file);

        if (file == null || file.isEmpty()) {
            logValidationError("File is empty or null");
            throw new RuntimeException("File is empty or null");
        }

        long fileSize = file.getSize();
        if (fileSize > MAX_FILE_SIZE) {
            logValidationError("File size exceeds 10MB limit. Size: " + fileSize + " bytes");
            throw new RuntimeException("File size exceeds 10MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_DOCUMENT_TYPES.contains(contentType.toLowerCase())) {
            logValidationError("Invalid file type. Only PDF, DOC, DOCX, XLS, XLSX are allowed. Got: " + contentType);
            throw new RuntimeException(
                    "Invalid file type. Only PDF, DOC, DOCX, XLS, XLSX are allowed. Got: " + contentType
            );
        }

        // Validate filename
        String filename = file.getOriginalFilename();
        if (filename == null || filename.contains("..")) {
            logValidationError("Invalid filename: " + filename);
            throw new RuntimeException("Invalid filename");
        }

        logValidationSuccess("document", file);
    }

    private static void logValidationStart(String fileType, MultipartFile file) {
        System.out.println("[FILE-VALIDATOR] [VALIDATE-" + fileType.toUpperCase() + "] Started - " +
                "Filename: " + file.getOriginalFilename() + ", " +
                "Size: " + file.getSize() + " bytes, " +
                "Content-Type: " + file.getContentType());
    }

    private static void logValidationSuccess(String fileType, MultipartFile file) {
        System.out.println("[FILE-VALIDATOR] [VALIDATE-" + fileType.toUpperCase() + "] SUCCESS - " +
                "File validated: " + file.getOriginalFilename());
    }

    private static void logValidationError(String message) {
        System.err.println("[FILE-VALIDATOR] [VALIDATION-ERROR] " + message);
    }

    /**
     * Get maximum file size
     */
    public static long getMaxFileSize() {
        return MAX_FILE_SIZE;
    }

    /**
     * Get allowed image types
     */
    public static List<String> getAllowedImageTypes() {
        return ALLOWED_IMAGE_TYPES;
    }

    /**
     * Get allowed document types
     */
    public static List<String> getAllowedDocumentTypes() {
        return ALLOWED_DOCUMENT_TYPES;
    }
}