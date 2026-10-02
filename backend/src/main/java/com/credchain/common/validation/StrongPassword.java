package com.credchain.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Single source of truth for the password policy:
 * 8-72 chars, at least one uppercase, lowercase, digit and special character.
 * (72 = BCrypt's maximum input length.)
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@NotBlank
@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,72}$")
@ReportAsSingleViolation
public @interface StrongPassword {

    String message() default "Password must be 8-72 characters with uppercase, lowercase, digit and special character";

    Class<?>[] groups() default {};


    Class<? extends Payload>[] payload() default {};
}