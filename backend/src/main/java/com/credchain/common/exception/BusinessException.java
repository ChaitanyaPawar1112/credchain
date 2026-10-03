package com.credchain.common.exception;

import lombok.Getter;

/**
 * Throw this from any service when a business rule is broken.
 * Example: throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}