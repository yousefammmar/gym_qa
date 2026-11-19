package com.gymtracker.service;

import org.springframework.http.HttpStatus;

/**
 * Every business-rule violation is thrown as an ApiException.
 * code = the requirement ID from docs/requirements.md (e.g. "WKT-01"), so a tester can
 * trace an HTTP response straight back to the rule that produced it.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    /** Used both for missing records and for records owned by another user (rule AUTH-11). */
    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT-FOUND", what + " not found");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
