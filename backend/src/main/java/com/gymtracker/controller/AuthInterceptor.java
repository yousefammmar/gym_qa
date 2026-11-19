package com.gymtracker.controller;

import com.gymtracker.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * AUTH-08: runs before every protected /api call (see WebConfig for the public ones).
 * A valid token puts the user's id in the request attribute "userId"; otherwise AuthService throws 401.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID = "userId";

    private final AuthService auth;

    public AuthInterceptor(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long userId = auth.userIdForToken(request.getHeader("Authorization"));
        request.setAttribute(USER_ID, userId);
        return true;
    }
}
