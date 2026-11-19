package com.gymtracker.controller;

import com.gymtracker.model.AuthDtos.AuthResponse;
import com.gymtracker.model.AuthDtos.LoginRequest;
import com.gymtracker.model.AuthDtos.PasswordChangeRequest;
import com.gymtracker.model.AuthDtos.ProfileUpdateRequest;
import com.gymtracker.model.AuthDtos.RegisterRequest;
import com.gymtracker.model.AuthDtos.UserView;
import com.gymtracker.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** /api/auth/* (register, login, logout) and /api/profile. */
@RestController
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/api/auth/login")
    public AuthResponse login(@RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String authorization) {
        auth.logout(authorization);
    }

    @GetMapping("/api/profile")
    public UserView profile(@RequestAttribute(AuthInterceptor.USER_ID) long userId) {
        return auth.profile(userId);
    }

    @PutMapping("/api/profile")
    public UserView updateProfile(@RequestAttribute(AuthInterceptor.USER_ID) long userId,
                                  @RequestBody ProfileUpdateRequest req) {
        return auth.updateProfile(userId, req);
    }

    @PutMapping("/api/profile/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestAttribute(AuthInterceptor.USER_ID) long userId,
                               @RequestHeader("Authorization") String authorization,
                               @RequestBody PasswordChangeRequest req) {
        auth.changePassword(userId, authorization, req);
    }
}
