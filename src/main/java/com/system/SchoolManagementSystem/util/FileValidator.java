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
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is empty or null");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("File size exceeds 10MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new RuntimeException(
                    "Invalid file type. Only JPG, PNG, GIF, WEBP are allowed. Got: " + contentType
            );
        }

        // Validate filename
        String filename = file.getOriginalFilename();
        if (filename == null || filename.contains("..")) {
            throw new RuntimeException("Invalid filename");
        }
    }

    /**
     * Validate document file
     */
    public static void validateDocumentFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is empty or null");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("File size exceeds 10MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_DOCUMENT_TYPES.contains(contentType.toLowerCase())) {
            throw new RuntimeException(
                    "Invalid file type. Only PDF, DOC, DOCX, XLS, XLSX are allowed. Got: " + contentType
            );
        }

        // Validate filename
        String filename = file.getOriginalFilename();
        if (filename == null || filename.contains("..")) {
            throw new RuntimeException("Invalid filename");
        }
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