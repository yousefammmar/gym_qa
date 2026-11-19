package com.gymtracker.service;

import com.gymtracker.model.GoalType;

import java.time.LocalDate;

/** Goal input rules (GOL-01 .. GOL-05). */
public final class GoalValidator {

    public static final int TITLE_MAX = 80;
    public static final double MAX_WEIGHT_TARGET = 500.0;

    private GoalValidator() {
    }

    /** GOL-01: 1-80 characters after trimming. */
    public static String title(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > TITLE_MAX) {
            throw ApiException.badRequest("GOL-01", "Goal title must be 1–80 characters");
        }
        return value;
    }

    /** GOL-04: type is required and must be one of the GoalType values. */
    public static GoalType type(String raw) {
        String value = raw == null ? "" : raw.trim();
        try {
            return GoalType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("GOL-04", "Goal type must be WORKOUT_COUNT, TOTAL_VOLUME or EXERCISE_MAX_WEIGHT");
        }
    }

    /**
     * GOL-02: target must be positive.
     * GOL-05: a weight goal cannot exceed the 500 kg logging limit; a workout-count goal must be a whole number.
     */
    public static double targetValue(GoalType type, Double target) {
        if (target == null || target <= 0) {
            throw ApiException.badRequest("GOL-02", "Target value must be greater than 0");
        }
        if (type == GoalType.EXERCISE_MAX_WEIGHT && target > MAX_WEIGHT_TARGET) {
            throw ApiException.badRequest("GOL-05", "A weight target cannot be above 500 kg");
        }
        if (type == GoalType.WORKOUT_COUNT && target != Math.floor(target)) {
            throw ApiException.badRequest("GOL-05", "A workout-count target must be a whole number");
        }
        return target;
    }

    /**
     * GOL-03: the target date is required and cannot be in the past when a goal is created.
     * When an existing goal is edited, an unchanged (possibly past) date is kept, but a changed date
     * must again be today or later.
     *
     * @param previous the stored date, or null when creating a new goal
     */
    public static LocalDate targetDate(LocalDate date, LocalDate previous, LocalDate today) {
        if (date == null) {
            throw ApiException.badRequest("GOL-03", "Choose a target date");
        }
        // previous is null for a new goal, and date.equals(null) is false, so a new goal always counts as "changed".
        // (Mutation testing showed an extra "previous == null ||" check here was redundant.)
        boolean isNewOrChanged = !date.equals(previous);
        if (isNewOrChanged && date.isBefore(today)) {
            throw ApiException.badRequest("GOL-03", "Target date cannot be in the past");
        }
        return date;
    }
}
