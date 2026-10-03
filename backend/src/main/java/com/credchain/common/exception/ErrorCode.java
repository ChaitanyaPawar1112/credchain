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
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "This action is not allowed in the current state"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later"),

    // ---------- Authentication / Authorization ----------
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Token is invalid or has expired"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account is temporarily locked due to failed login attempts"),
    ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "Account is not active"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "You must change your temporary password first"),

    // ---------- User ----------
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "An account with this email already exists"),
    CURRENT_PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "Current password is incorrect"),


    // ---------- Institution ----------
    INSTITUTION_ALREADY_EXISTS(HttpStatus.CONFLICT, "An institution with this code, registration number or wallet already exists"),
    INSTITUTION_NOT_APPROVED(HttpStatus.FORBIDDEN, "Institution is not approved"),

    // ---------- Student ----------
    STUDENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "A student with this enrollment number already exists"),
    STUDENT_ALREADY_LINKED(HttpStatus.CONFLICT, "This student record is already linked to an account"),
    INVALID_CLAIM_CODE(HttpStatus.BAD_REQUEST, "Claim code is invalid or has expired");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}