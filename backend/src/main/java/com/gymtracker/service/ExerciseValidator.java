package com.gymtracker.service;

import com.gymtracker.model.MuscleGroup;

/** Custom exercise and filter input rules (EXR-03, EXR-04, EXR-05, EXR-07). */
public final class ExerciseValidator {

    public static final int NAME_MIN = 2;
    public static final int NAME_MAX = 50;
    public static final int DESCRIPTION_MAX = 200;

    private ExerciseValidator() {
    }

    /** EXR-04: 2-50 characters after trimming. */
    public static String name(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < NAME_MIN || value.length() > NAME_MAX) {
            throw ApiException.badRequest("EXR-04", "Exercise name must be 2–50 characters");
        }
        return value;
    }

    /** EXR-07: optional, at most 200 characters. */
    public static String description(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() > DESCRIPTION_MAX) {
            throw ApiException.badRequest("EXR-07", "Description must be at most 200 characters");
        }
        return value;
    }

    /**
     * Parses a muscle group name (case-insensitive).
     * @param required true when creating an exercise (EXR-05); false for the optional list filter (EXR-03),
     *                 where blank means "all muscle groups" and null is returned.
     */
    public static MuscleGroup muscleGroup(String raw, boolean required) {
        String value = raw == null ? "" : raw.trim();
        String code = required ? "EXR-05" : "EXR-03";
        if (value.isEmpty()) {
            if (required) {
                throw ApiException.badRequest(code, "Choose a muscle group");
            }
            return null;
        }
        try {
            return MuscleGroup.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(code, "Unknown muscle group: " + value);
        }
    }
}
