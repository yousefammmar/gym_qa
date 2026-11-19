package com.gymtracker.model;

/** An exercise in the library. ownerId == null means built-in; otherwise it is that user's custom exercise. */
public record Exercise(long id, String name, MuscleGroup muscleGroup, String description, Long ownerId) {

    /** Serialised to JSON as "custom". */
    public boolean isCustom() {
        return ownerId != null;
    }
}
