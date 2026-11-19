package com.gymtracker.service;


import java.util.regex.Pattern;

/**
 * Registration and profile input rules (AUTH-01, AUTH-02, AUTH-03, AUTH-09).
 * Each method returns the cleaned value or throws ApiException with the rule ID.
 */
public final class UserValidator {

    public static final int USERNAME_MIN = 3;
    public static final int USERNAME_MAX = 30;
    public static final int EMAIL_MAX = 100;
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 64;
    public static final int DISPLAY_NAME_MAX = 50;

    private static final Pattern USERNAME_CHARS = Pattern.compile("[A-Za-z0-9_]+");
    private static final Pattern EMAIL_FORMAT = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");

    private UserValidator() {
    }

    /** AUTH-01: 3-30 characters, only letters, digits and underscore. Surrounding spaces are trimmed. */
    public static String username(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < USERNAME_MIN || value.length() > USERNAME_MAX || !USERNAME_CHARS.matcher(value).matches()) {
            throw ApiException.badRequest("AUTH-01",
                    "Username must be 3–30 characters using only letters, digits or underscore");
        }
        return value;
    }

    /** AUTH-02: name@domain.tld, at most 100 characters. Stored in lower case. */
    public static String email(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase();
        if (value.length() > EMAIL_MAX || !EMAIL_FORMAT.matcher(value).matches()) {
            throw ApiException.badRequest("AUTH-02", "Enter a valid email address (max 100 characters)");
        }
        return value;
    }

    /** AUTH-03: 8-64 characters with at least one letter and at least one digit. Not trimmed. */
    public static void password(String raw) {
        if (raw == null || raw.length() < PASSWORD_MIN || raw.length() > PASSWORD_MAX) {
            throw ApiException.badRequest("AUTH-03", "Password must be 8–64 characters long");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : raw.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw ApiException.badRequest("AUTH-03", "Password must contain at least one letter and one digit");
        }
    }

    /** AUTH-09: blank means "use the fallback"; otherwise 1-50 characters after trimming. */
    public static String displayName(String raw, String fallback) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return fallback;
        }
        if (value.length() > DISPLAY_NAME_MAX) {
            throw ApiException.badRequest("AUTH-09", "Display name must be at most 50 characters");
        }
        return value;
    }
}
