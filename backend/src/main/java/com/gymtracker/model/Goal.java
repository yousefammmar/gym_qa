package com.gymtracker.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A fitness goal. exerciseId is only set for EXERCISE_MAX_WEIGHT goals. */
public record Goal(long id, long userId, String title, GoalType type, Long exerciseId, double targetValue,
                   LocalDate startDate, LocalDate targetDate, GoalStatus status, LocalDateTime completedAt) {
}
