package com.gymtracker.model;


import java.time.LocalDate;
import java.util.List;

/** Response bodies for /api/progress and /api/dashboard. */
public final class ProgressDtos {

    private ProgressDtos() {
    }

    /** One bar of the weekly chart. weekStart is always a Monday. */
    public record WeekStat(LocalDate weekStart, int completedWorkouts, double volume) {
    }

    /** "3 of 5 training days done this week" (the original tracker bar). */
    public record ThisWeek(LocalDate weekStart, int completedWorkouts, int plannedWorkouts, int percent) {
    }

    public record ProgressResponse(int totalCompletedWorkouts, double totalVolume, int currentStreak,
                                   int longestStreak, ThisWeek thisWeek, List<WeekStat> weekly) {
    }

    /** One point of an exercise progression chart (one completed workout day). */
    public record ExercisePoint(LocalDate date, double maxWeightKg, double volume, int sets) {
    }

    /** planDay is null on rest days (Thursday, Sunday). */
    public record TodayPlan(LocalDate date, int weekday, PlanDay planDay) {
    }

    public record DashboardResponse(String displayName, ProgressResponse progress, TodayPlan today,
                                    WorkoutDtos.WorkoutSummary activeWorkout,
                                    List<WorkoutDtos.WorkoutSummary> recentWorkouts,
                                    List<GoalDtos.GoalView> activeGoals) {
    }
}
