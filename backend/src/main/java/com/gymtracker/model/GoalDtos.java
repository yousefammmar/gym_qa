package com.gymtracker.model;


import java.time.LocalDate;
import java.time.LocalDateTime;

/** Request/response bodies for /api/goals. */
public final class GoalDtos {

    private GoalDtos() {
    }

    /** On update only title, targetValue and targetDate are used; type and exercise are fixed at creation. */
    public record GoalRequest(String title, String type, Long exerciseId, Double targetValue, LocalDate targetDate) {
    }

    public record GoalView(long id, String title, GoalType type, Long exerciseId, String exerciseName,
                           double targetValue, double currentValue, int percent, GoalStatus status, GoalState state,
                           LocalDate startDate, LocalDate targetDate, LocalDateTime completedAt) {
    }
}
