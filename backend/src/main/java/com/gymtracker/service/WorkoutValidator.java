package com.gymtracker.service;


/**
 * Workout logging limits (WKT-01 .. WKT-04, WKT-08). These are the proposed application
 * specifications; docs/requirements.md and the boundary tests use exactly these numbers.
 */
public final class WorkoutValidator {

    public static final int REPS_MIN = 1;
    public static final int REPS_MAX = 100;
    public static final double WEIGHT_MIN = 0.0;
    public static final double WEIGHT_MAX = 500.0;
    public static final int SETS_MIN = 1;
    public static final int SETS_MAX = 20;
    public static final int DURATION_MIN = 1;
    public static final int DURATION_MAX = 300;
    public static final int NAME_MAX = 60;
    public static final int NOTES_MAX = 500;
    public static final int TARGET_REPS_MAX = 20;

    private WorkoutValidator() {
    }

    public static boolean isValidReps(Integer reps) {
        return reps != null && reps >= REPS_MIN && reps <= REPS_MAX;
    }

    /** 0 kg is allowed (body-weight exercises such as planks). */
    public static boolean isValidWeight(Double weightKg) {
        return weightKg != null && weightKg >= WEIGHT_MIN && weightKg <= WEIGHT_MAX;
    }

    public static boolean isValidSetCount(Integer sets) {
        return sets != null && sets >= SETS_MIN && sets <= SETS_MAX;
    }

    public static boolean isValidDuration(Integer minutes) {
        return minutes != null && minutes >= DURATION_MIN && minutes <= DURATION_MAX;
    }

    /** WKT-01 */
    public static void requireReps(Integer reps) {
        if (!isValidReps(reps)) {
            throw ApiException.badRequest("WKT-01", "Repetitions must be a whole number from 1 to 100");
        }
    }

    /** WKT-02 */
    public static void requireWeight(Double weightKg) {
        if (!isValidWeight(weightKg)) {
            throw ApiException.badRequest("WKT-02", "Weight must be from 0 to 500 kg");
        }
    }

    /** WKT-03 */
    public static void requireSetCount(Integer sets) {
        if (!isValidSetCount(sets)) {
            throw ApiException.badRequest("WKT-03", "Number of sets must be from 1 to 20");
        }
    }

    /** WKT-04 */
    public static void requireDuration(Integer minutes) {
        if (!isValidDuration(minutes)) {
            throw ApiException.badRequest("WKT-04", "Workout duration must be from 1 to 300 minutes");
        }
    }

    /** WKT-08: blank name falls back to the plan name; at most 60 characters. */
    public static String name(String raw, String fallback) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            value = fallback;
        }
        if (value.length() > NAME_MAX) {
            throw ApiException.badRequest("WKT-08", "Workout name must be at most 60 characters");
        }
        return value;
    }

    /** WKT-08: notes are optional, at most 500 characters. */
    public static String notes(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() > NOTES_MAX) {
            throw ApiException.badRequest("WKT-08", "Notes must be at most 500 characters");
        }
        return value;
    }

    /** WKT-03: free text such as "6–10" or "to form-failure", at most 20 characters. */
    public static String targetReps(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() > TARGET_REPS_MAX) {
            throw ApiException.badRequest("WKT-03", "Target reps text must be at most 20 characters");
        }
        return value;
    }
}
