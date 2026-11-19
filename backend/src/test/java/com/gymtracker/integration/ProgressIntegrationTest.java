package com.gymtracker.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Workout logging -> progress calculation through the API and the database (PRG-01 .. PRG-07). */
@DisplayName("Progress integration")
class ProgressIntegrationTest extends IntegrationTestBase {

    /** Creates a plan workout on a date, logs one set (reps x kg) and optionally completes it. */
    private long workout(String auth, String slug, LocalDate date, int reps, double kg, boolean complete) throws Exception {
        JsonNode w = createWorkout(auth, Map.of("planSlug", slug, "workoutDate", date.toString()));
        long id = w.get("id").asLong();
        logSet(auth, id, w.get("exercises").get(0).get("id").asLong(), reps, kg);
        if (complete) {
            mvc.perform(postAs(auth, "/api/workouts/" + id + "/complete", Map.of("durationMinutes", 40))).andExpect(status().isOk());
        }
        return id;
    }

    @Test
    @DisplayName("new user: everything is zero")
    void emptyProgress() throws Exception {
        String auth = register("fresh");
        mvc.perform(getAs(auth, "/api/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCompletedWorkouts").value(0))
                .andExpect(jsonPath("$.totalVolume").value(0.0))
                .andExpect(jsonPath("$.currentStreak").value(0))
                .andExpect(jsonPath("$.thisWeek.plannedWorkouts").value(5))
                .andExpect(jsonPath("$.weekly.length()").value(8));
    }

    @Test
    @DisplayName("only completed workouts and their completed sets count")
    void completedOnly() throws Exception {
        String auth = register("lifter");
        LocalDate today = LocalDate.now();
        workout(auth, "push", today, 10, 50, true);                   // 500
        workout(auth, "pull", today.minusDays(1), 8, 40, true);       // 320
        workout(auth, "legs", today, 5, 100, false);                  // in progress: ignored

        mvc.perform(getAs(auth, "/api/progress"))
                .andExpect(jsonPath("$.totalCompletedWorkouts").value(2))
                .andExpect(jsonPath("$.totalVolume").value(820.0))
                .andExpect(jsonPath("$.currentStreak").value(2))
                .andExpect(jsonPath("$.longestStreak").value(2))
                .andExpect(jsonPath("$.weekly[7].weekStart").value(today.with(java.time.DayOfWeek.MONDAY).toString()));

        mvc.perform(getAs(auth, "/api/workouts?status=COMPLETED"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Push Day")); // newest first
        mvc.perform(getAs(auth, "/api/workouts?status=IN_PROGRESS")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("exercise progression reads completed sets of completed workouts per day")
    void exerciseProgression() throws Exception {
        String auth = register("lifter");
        LocalDate today = LocalDate.now();
        workout(auth, "push", today.minusDays(7), 8, 30, true);   // Dumbbell Press (exercise 1)
        workout(auth, "push", today, 8, 32.5, true);
        workout(auth, "push", today, 8, 40, false);               // not completed

        mvc.perform(getAs(auth, "/api/progress/exercises/1"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].maxWeightKg").value(30.0))
                .andExpect(jsonPath("$[1].maxWeightKg").value(32.5))
                .andExpect(jsonPath("$[1].volume").value(260.0));
    }

    @Test
    @DisplayName("dashboard combines progress, today's plan day, the active workout and goals")
    void dashboard() throws Exception {
        String auth = register("lifter");
        long active = workout(auth, "push", LocalDate.now(), 8, 30, false);
        int weekday = LocalDate.now().getDayOfWeek().getValue();
        mvc.perform(getAs(auth, "/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("lifter"))
                .andExpect(jsonPath("$.today.weekday").value(weekday))
                .andExpect(jsonPath("$.activeWorkout.id").value(active))
                .andExpect(jsonPath("$.recentWorkouts.length()").value(1))
                .andExpect(jsonPath("$.progress.totalCompletedWorkouts").value(0));
    }
}
