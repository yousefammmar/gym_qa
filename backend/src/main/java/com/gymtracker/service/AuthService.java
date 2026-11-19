package com.gymtracker.service;

import com.gymtracker.model.AuthDtos.AuthResponse;
import com.gymtracker.model.AuthDtos.LoginRequest;
import com.gymtracker.model.AuthDtos.PasswordChangeRequest;
import com.gymtracker.model.AuthDtos.ProfileUpdateRequest;
import com.gymtracker.model.AuthDtos.RegisterRequest;
import com.gymtracker.model.AuthDtos.UserView;
import com.gymtracker.model.User;
import com.gymtracker.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Registration, login/logout, tokens and profile (AUTH-01 .. AUTH-10).
 * A login creates a random token stored in auth_tokens; the browser sends it as
 * "Authorization: Bearer <token>". Tokens expire after 7 days (AUTH-08).
 */
@Service
public class AuthService {

    public static final Duration TOKEN_LIFETIME = Duration.ofDays(7);
    private static final String BEARER = "Bearer ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    /** AUTH-01..05: validate, reject duplicates, store a BCrypt hash, sign the new user in. */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String username = UserValidator.username(req.username());
        String email = UserValidator.email(req.email());
        UserValidator.password(req.password());
        String displayName = UserValidator.displayName(req.displayName(), username);

        if (users.usernameTaken(username)) {
            throw ApiException.conflict("AUTH-04", "That username is already taken");
        }
        if (users.emailTaken(email, -1)) {
            throw ApiException.conflict("AUTH-04", "That email is already registered");
        }
        long id = users.insert(username, email, encoder.encode(req.password()), displayName, LocalDateTime.now(clock));
        return issueToken(users.findById(id).orElseThrow());
    }

    /** AUTH-06: same message for unknown user and wrong password, so usernames cannot be probed. */
    @Transactional
    public AuthResponse login(LoginRequest req) {
        String username = req.username() == null ? "" : req.username().trim();
        String password = req.password() == null ? "" : req.password();
        if (username.isEmpty() || password.isEmpty()) {
            throw ApiException.badRequest("AUTH-06", "Enter your username and password");
        }
        User user = users.findByUsername(username).orElse(null);
        if (user == null || !encoder.matches(password, user.passwordHash())) {
            throw ApiException.unauthorized("AUTH-06", "Wrong username or password");
        }
        return issueToken(user);
    }

    /** AUTH-07 */
    public void logout(String authorizationHeader) {
        users.deleteToken(tokenFrom(authorizationHeader));
    }

    /** AUTH-08: returns the user id for a valid, unexpired token. Used by AuthInterceptor on every /api call. */
    public long userIdForToken(String authorizationHeader) {
        String token = tokenFrom(authorizationHeader);
        UserRepository.TokenRow row = users.findToken(token).orElse(null);
        if (row == null) {
            throw ApiException.unauthorized("AUTH-08", "Please log in");
        }
        if (isExpired(row.createdAt(), LocalDateTime.now(clock))) {
            users.deleteToken(token);
            throw ApiException.unauthorized("AUTH-08", "Your session expired, please log in again");
        }
        return row.userId();
    }

    /** A token is valid for exactly TOKEN_LIFETIME; at the boundary instant it is already expired. */
    public static boolean isExpired(LocalDateTime issuedAt, LocalDateTime now) {
        return !now.isBefore(issuedAt.plus(TOKEN_LIFETIME));
    }

    public UserView profile(long userId) {
        return view(users.findById(userId).orElseThrow(() -> ApiException.notFound("User")));
    }

    /** AUTH-02, AUTH-04, AUTH-09 */
    @Transactional
    public UserView updateProfile(long userId, ProfileUpdateRequest req) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        String email = UserValidator.email(req.email());
        String displayName = UserValidator.displayName(req.displayName(), user.username());
        if (users.emailTaken(email, userId)) {
            throw ApiException.conflict("AUTH-04", "That email is already registered");
        }
        users.updateProfile(userId, displayName, email);
        return profile(userId);
    }

    /** AUTH-10: current password must match; new one follows AUTH-03. Other sessions are signed out. */
    @Transactional
    public void changePassword(long userId, String authorizationHeader, PasswordChangeRequest req) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (req.currentPassword() == null || !encoder.matches(req.currentPassword(), user.passwordHash())) {
            throw ApiException.badRequest("AUTH-10", "Current password is incorrect");
        }
        UserValidator.password(req.newPassword());
        users.updatePassword(userId, encoder.encode(req.newPassword()));
        users.deleteOtherTokens(userId, tokenFrom(authorizationHeader));
    }

    private AuthResponse issueToken(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        users.insertToken(token, user.id(), LocalDateTime.now(clock));
        return new AuthResponse(token, view(user));
    }

    private static String tokenFrom(String header) {
        if (header == null || !header.startsWith(BEARER) || header.length() == BEARER.length()) {
            throw ApiException.unauthorized("AUTH-08", "Please log in");
        }
        return header.substring(BEARER.length()).trim();
    }

    private static UserView view(User u) {
        return new UserView(u.id(), u.username(), u.email(), u.displayName(), u.createdAt());
    }
}
