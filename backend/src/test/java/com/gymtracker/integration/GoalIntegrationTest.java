package com.gymtracker.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Goals through the API with progress calculated from stored workouts (GOL-01 .. GOL-10). */
@DisplayName("Goal integration")
class GoalIntegrationTest extends IntegrationTestBase {

    private final String nextMonth = LocalDate.now().plusMonths(1).toString();

    private void completedWorkout(String auth, double kg) throws Exception {
        JsonNode w = createWorkout(auth, Map.of("planSlug", "push"));
        long id = w.get("id").asLong();
        logSet(auth, id, w.get("exercises").get(0).get("id").asLong(), 5, kg);
        mvc.perform(postAs(auth, "/api/workouts/" + id + "/complete", Map.of("durationMinutes", 30))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("validation: past date, non-positive target, unknown type, weight goal without exercise")
    void validation() throws Exception {
        String auth = register("lifter");
        mvc.perform(postAs(auth, "/api/goals", Map.of("title", "x", "type", "WORKOUT_COUNT", "targetValue", 5,
                        "targetDate", LocalDate.now().minusDays(1).toString())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GOL-03"));
        mvc.perform(postAs(auth, "/api/goals", Map.of("title", "x", "type", "WORKOUT_COUNT", "targetValue", 0, "targetDate", nextMonth)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GOL-02"));
        mvc.perform(postAs(auth, "/api/goals", Map.of("title", "x", "type", "RUN_FAST", "targetValue", 5, "targetDate", nextMonth)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GOL-04"));
        mvc.perform(postAs(auth, "/api/goals", Map.of("title", "x", "type", "EXERCISE_MAX_WEIGHT", "targetValue", 50, "targetDate", nextMonth)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GOL-04"));
        mvc.perform(postAs(auth, "/api/goals", Map.of("title", "x", "type", "WORKOUT_COUNT", "targetValue", 5,
                        "targetDate", LocalDate.now().toString())))
                .andExpect(status().isCreated()); // today is allowed
    }

    @Test
    @DisplayName("progress comes from stored workouts; complete only at 100 %; completed goals are read-only")
    void progressAndCompletion() throws Exception {
        String auth = register("lifter");
        JsonNode goal = read(mvc.perform(postAs(auth, "/api/goals",
                Map.of("title", "Two sessions", "type", "WORKOUT_COUNT", "targetValue", 2, "targetDate", nextMonth))).andReturn());
        long id = goal.get("id").asLong();
        mvc.perform(post("/api/goals/" + id + "/complete").header("Authorization", auth))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GOL-08"));

        completedWorkout(auth, 40);
        mvc.perform(getAs(auth, "/api/goals"))
                .andExpect(jsonPath("$[0].currentValue").value(1.0))
                .andExpect(jsonPath("$[0].percent").value(50))
                .andExpect(jsonPath("$[0].state").value("ACTIVE"));

        completedWorkout(auth, 40);
        mvc.perform(getAs(auth, "/api/goals"))
                .andExpect(jsonPath("$[0].percent").value(100))
                .andExpect(jsonPath("$[0].state").value("ACHIEVED"));

        mvc.perform(post("/api/goals/" + id + "/complete").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(putAs(auth, "/api/goals/" + id, Map.of("title", "Three", "targetValue", 3, "targetDate", nextMonth)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GOL-09"));
    }

    @Test
    @DisplayName("weight and volume goals; edit; delete; other users cannot see them")
    void weightGoalAndIsolation() throws Exception {
        String auth = register("lifter");
        long id = read(mvc.perform(postAs(auth, "/api/goals", Map.of("title", "Press 40", "type", "EXERCISE_MAX_WEIGHT",
                "exerciseId", 1, "targetValue", 40, "targetDate", nextMonth))).andReturn()).get("id").asLong();
        mvc.perform(postAs(auth, "/api/goals", Map.of("title", "Volume", "type", "TOTAL_VOLUME", "targetValue", 1000, "targetDate", nextMonth)))
                .andExpect(status().isCreated());
        completedWorkout(auth, 30);   // Dumbbell Press 5 x 30 kg

        mvc.perform(getAs(auth, "/api/goals"))
                .andExpect(jsonPath("$[?(@.title == 'Press 40')].currentValue").value(30.0))
                .andExpect(jsonPath("$[?(@.title == 'Press 40')].percent").value(75))
                .andExpect(jsonPath("$[?(@.title == 'Press 40')].exerciseName").value("Dumbbell Press"))
                .andExpect(jsonPath("$[?(@.title == 'Volume')].currentValue").value(150.0));

        mvc.perform(putAs(auth, "/api/goals/" + id, Map.of("title", "Press 30", "targetValue", 30, "targetDate", nextMonth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.percent").value(100));

        String other = register("other");
        mvc.perform(getAs(other, "/api/goals")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(deleteAs(other, "/api/goals/" + id)).andExpect(status().isNotFound());
        mvc.perform(deleteAs(auth, "/api/goals/" + id)).andExpect(status().isNoContent());
        mvc.perform(getAs(auth, "/api/goals")).andExpect(jsonPath("$.length()").value(1));
    }
}
