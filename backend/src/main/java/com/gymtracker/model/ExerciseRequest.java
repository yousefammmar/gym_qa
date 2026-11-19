package com.gymtracker.model;

/** Body of POST /api/exercises (create a custom exercise). */
public record ExerciseRequest(String name, String muscleGroup, String description) {
}
