package com.gymtracker.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * System-level scenario (SC-01 in docs/test-plan.md): one user's full journey through the REST API,
 * in the same order the website calls it. Re-run after every change as the regression smoke test.
 */
@DisplayName("End-to-end scenario SC-01")
class EndToEndScenarioTest extends IntegrationTestBase {

    @Test
    @DisplayName("register -> plan -> log Push Day -> complete -> progress -> goal reached -> logout")
    void fullJourney() throws Exception {
        // 1. register and see the plan
        String auth = register("journey");
        mvc.perform(get("/api/plan")).andExpect(jsonPath("$[0].name").value("Push Day"));

        // 2. set a goal before training
        long goalId = read(mvc.perform(postAs(auth, "/api/goals", Map.of("title", "First session", "type", "WORKOUT_COUNT",
                "targetValue", 1, "targetDate", LocalDate.now().plusDays(14).toString()))).andReturn()).get("id").asLong();

        // 3. start Push Day and log three sets of Dumbbell Press, one un-ticked
        JsonNode workout = createWorkout(auth, Map.of("planSlug", "push"));
        long id = workout.get("id").asLong();
        mvc.perform(post("/api/workouts/" + id + "/start").header("Authorization", auth)).andExpect(status().isOk());
        long press = workout.get("exercises").get(0).get("id").asLong();
        logSet(auth, id, press, 10, 30);
        logSet(auth, id, press, 8, 32.5);
        mvc.perform(postAs(auth, "/api/workouts/" + id + "/sets", Map.of("workoutExerciseId", press, "reps", 6, "weightKg", 35, "completed", false)))
                .andExpect(jsonPath("$.completedSets").value(2))
                .andExpect(jsonPath("$.volume").value(560.0));

        // 4. progress ignores the unfinished workout
        mvc.perform(getAs(auth, "/api/progress")).andExpect(jsonPath("$.totalCompletedWorkouts").value(0));

        // 5. complete it
        mvc.perform(postAs(auth, "/api/workouts/" + id + "/complete", Map.of("durationMinutes", 48)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        // 6. progress, weekly tracker and history reflect it
        mvc.perform(getAs(auth, "/api/progress"))
                .andExpect(jsonPath("$.totalCompletedWorkouts").value(1))
                .andExpect(jsonPath("$.totalVolume").value(560.0))
                .andExpect(jsonPath("$.thisWeek.completedWorkouts").value(1))
                .andExpect(jsonPath("$.thisWeek.percent").value(20))
                .andExpect(jsonPath("$.currentStreak").value(1));

        // 7. the goal is reached and can be completed
        mvc.perform(post("/api/goals/" + goalId + "/complete").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("COMPLETED"));

        // 8. log out: the token stops working
        mvc.perform(post("/api/auth/logout").header("Authorization", auth)).andExpect(status().isNoContent());
        mvc.perform(getAs(auth, "/api/dashboard")).andExpect(status().isUnauthorized());

        // 9. log back in: the data is still there (persistence)
        String token = read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", "journey", "password", "lift4ever")))).andReturn()).get("token").asText();
        mvc.perform(getAs("Bearer " + token, "/api/workouts?status=COMPLETED"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].volume").value(560.0));
    }

    @Test
    @DisplayName("every page of the website (../frontend) is served by the same application")
    void staticSite() throws Exception {
        for (String page : new String[] {"/index.html", "/login.html", "/register.html", "/dashboard.html",
                "/workouts.html", "/workout.html", "/exercises.html", "/progress.html", "/goals.html", "/profile.html",
                "/css/style.css", "/js/common.js", "/js/charts.js"}) {
            mvc.perform(get(page)).andExpect(status().isOk());
        }
    }
}
