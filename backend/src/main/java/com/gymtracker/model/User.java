package com.gymtracker.model;

/** A registered user. passwordHash is a BCrypt hash, never the plain password (rule AUTH-05). */
public record User(long id, String username, String email, String passwordHash, String displayName, String createdAt) {
}
