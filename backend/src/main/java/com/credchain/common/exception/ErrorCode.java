package com.credchain.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Every error the API can return.
 * The frontend checks the enum name (e.g. "EMAIL_ALREADY_EXISTS"),
 * so never rename an existing value once it's in use.
 */
@Getter
public enum ErrorCode {

    // ---------- Generic ----------
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "One or more fields are invalid"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "The request is not valid"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later"),

    // ---------- Authentication / Authorization ----------
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Token is invalid or has expired"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account is temporarily locked due to failed login attempts"),
    ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "Account is not active"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),

    // ---------- User ----------
    // ---------- User ----------
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "An account with this email already exists"),
    CURRENT_PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "Current password is incorrect");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}