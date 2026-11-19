package com.gymtracker.controller;

import com.gymtracker.service.ApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.ErrorResponse;

/** Turns exceptions into JSON: {"code": "WKT-01", "message": "..."}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiError(String code, String message) {
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ApiError(ex.getCode(), ex.getMessage()));
    }

    /** Broken JSON, wrong types ("reps": "ten") or decimals where a whole number is required. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError("REQ-01", "Request body is missing, malformed or has a wrong value type"));
    }

    /** e.g. /api/workouts/abc or ?status=DONE */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError("REQ-02", "Invalid value for parameter '" + ex.getName() + "'"));
    }

    /** Anything else. Spring's own errors (404, 405, 415, missing header ...) keep their HTTP status. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        if (ex instanceof ErrorResponse springError) {
            return ResponseEntity.status(springError.getStatusCode())
                    .body(new ApiError("REQ-00", springError.getBody().getDetail()));
        }
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("SRV-01", "Unexpected server error"));
    }
}
