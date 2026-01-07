package com.system.SchoolManagementSystem.auth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AuthException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public AuthException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
        this.errorCode = "AUTH_ERROR";
    }

    public AuthException(String message, HttpStatus status) {
        super(message);
        this.status = status;
        this.errorCode = "AUTH_ERROR";
    }

    public AuthException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}