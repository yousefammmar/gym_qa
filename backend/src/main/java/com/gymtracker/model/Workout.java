package com.gymtracker.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A workout session owned by one user. */
public record Workout(long id, long userId, String planSlug, String name, LocalDate workoutDate,
                      WorkoutStatus status, LocalDateTime startedAt, LocalDateTime completedAt,
                      Integer durationMinutes, String notes) {
}
