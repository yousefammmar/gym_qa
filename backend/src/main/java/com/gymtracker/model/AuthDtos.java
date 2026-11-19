package com.gymtracker.model;

/** Request/response bodies for /api/auth and /api/profile. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(String username, String email, String password, String displayName) {
    }

    public record LoginRequest(String username, String password) {
    }

    /** What the browser keeps: the token goes in "Authorization: Bearer <token>". */
    public record AuthResponse(String token, UserView user) {
    }

    /** A user without the password hash. */
    public record UserView(long id, String username, String email, String displayName, String createdAt) {
    }

    public record ProfileUpdateRequest(String displayName, String email) {
    }

    public record PasswordChangeRequest(String currentPassword, String newPassword) {
    }
}
