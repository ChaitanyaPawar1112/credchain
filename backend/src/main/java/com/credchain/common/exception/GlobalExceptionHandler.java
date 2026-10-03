package com.credchain.common.exception;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts every exception into an RFC 9457 Problem Details JSON response.
 * Extending ResponseEntityExceptionHandler means Spring's own errors
 * (404, 405, bad JSON, etc.) also come back in the same format.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Our own business rule violations. */
    @ExceptionHandler(BusinessException.class)

    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        log.debug("Business error [{}] at {}: {}", code, request.getRequestURI(), ex.getMessage());
        ProblemDetail body = build(code, ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(code.getStatus()).body(body);
    }
    /** No token, or token invalid/expired (sent here by RestAuthenticationEntryPoint). */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        ErrorCode code = (ex instanceof OAuth2AuthenticationException)
                ? ErrorCode.INVALID_TOKEN
                : ErrorCode.UNAUTHORIZED;
        return ResponseEntity.status(code.getStatus())
                .header("WWW-Authenticate", "Bearer")
                .body(build(code, code.getDefaultMessage(), request.getRequestURI()));
    }

    /** @PreAuthorize / role checks that fail inside controllers. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ErrorCode code = ErrorCode.ACCESS_DENIED;
        return ResponseEntity.status(code.getStatus())
                .body(build(code, code.getDefaultMessage(), request.getRequestURI()));
    }

    /** Anything unexpected: log full details, but never leak them to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {}", request.getRequestURI(), ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(build(code, code.getDefaultMessage(), request.getRequestURI()));
    }

    /** @Valid failures on request bodies: return which field failed and why. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()

                .forEach(err -> fieldErrors.putIfAbsent(err.getField(), err.getDefaultMessage()));

        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        ProblemDetail body = build(code, code.getDefaultMessage(), pathOf(request));
        body.setProperty("errors", fieldErrors);
        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // ---------- helpers ----------

    public static ProblemDetail build(ErrorCode code, String detail, String path) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.getStatus(), detail);
        pd.setTitle(code.getStatus().getReasonPhrase());
        if (path != null) {
            pd.setInstance(URI.create(path));
        }
        pd.setProperty("code", code.name());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    private static String pathOf(WebRequest request) {
        return (request instanceof ServletWebRequest swr) ? swr.getRequest().getRequestURI() : null;
    }
}