package com.gymtracker.model;

import java.util.List;

/** A training day of the original PPL + Upper/Lower program. weekday: 1 = Monday ... 7 = Sunday. */
public record PlanDay(String slug, String name, String focus, String prescription, int weekday,
                      List<PlanExercise> exercises) {
}
