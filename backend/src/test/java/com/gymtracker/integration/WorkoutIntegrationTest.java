package com.gymtracker.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Controller -> service -> SQLite for workout sessions (WKT-01 .. WKT-12, AUTH-11). */
@DisplayName("Workout integration")
class WorkoutIntegrationTest extends IntegrationTestBase {

    private String auth;
    private long workoutId;
    private long blockId;

    @BeforeEach
    void pushDay() throws Exception {
        auth = register("lifter");
        JsonNode workout = createWorkout(auth, Map.of("planSlug", "push"));
        workoutId = workout.get("id").asLong();
        blockId = workout.get("exercises").get(0).get("id").asLong();
    }

    private String setsUrl() {
        return "/api/workouts/" + workoutId + "/sets";
    }

    @Test
    @DisplayName("creating from the Push plan copies its 7 exercises and target sets")
    void createFromPlan() throws Exception {
        mvc.perform(getAs(auth, "/api/workouts/" + workoutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Push Day"))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.exercises.length()").value(7))
                .andExpect(jsonPath("$.exercises[0].exerciseName").value("Dumbbell Press"))
                .andExpect(jsonPath("$.exercises[0].targetSets").value(3));
        mvc.perform(postAs(auth, "/api/workouts", Map.of("planSlug", "arms")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-08"));
    }

    @Test
    @DisplayName("WKT-01/02 boundaries through the API; 5.5 reps is a type error (REQ-01)")
    void setBoundaries() throws Exception {
        mvc.perform(postAs(auth, setsUrl(), Map.of("workoutExerciseId", blockId, "reps", 0, "weightKg", 50)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-01"));
        mvc.perform(postAs(auth, setsUrl(), Map.of("workoutExerciseId", blockId, "reps", 101, "weightKg", 50)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-01"));
        mvc.perform(postAs(auth, setsUrl(), Map.of("workoutExerciseId", blockId, "reps", 8, "weightKg", -0.5)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-02"));
        mvc.perform(postAs(auth, setsUrl(), Map.of("workoutExerciseId", blockId, "reps", 8, "weightKg", 500.25)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-02"));
        mvc.perform(postAs(auth, setsUrl(), Map.of("workoutExerciseId", blockId, "reps", 5.5, "weightKg", 50)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQ-01"));

        mvc.perform(postAs(auth, setsUrl(), Map.of("workoutExerciseId", blockId, "reps", 1, "weightKg", 0)))
                .andExpect(status().isCreated());
        mvc.perform(postAs(auth, setsUrl(), Map.of("workoutExerciseId", blockId, "reps", 100, "weightKg", 500)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))      // first set started the workout
                .andExpect(jsonPath("$.volume").value(50000.0))
                .andExpect(jsonPath("$.completedSets").value(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workout_sets", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("WKT-03 an exercise holds at most 20 sets; target sets 1-20 when adding an exercise")
    void setCountLimit() throws Exception {
        for (int i = 0; i < 20; i++) {
            logSet(auth, workoutId, blockId, 5, 20);
        }
        mvc.perform(postAs(auth, setsUrl(), Map.of("workoutExerciseId", blockId, "reps", 5, "weightKg", 20)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-03"));
        mvc.perform(postAs(auth, "/api/workouts/" + workoutId + "/exercises", Map.of("exerciseId", 15, "targetSets", 21)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-03"));
        mvc.perform(postAs(auth, "/api/workouts/" + workoutId + "/exercises", Map.of("exerciseId", 15, "targetSets", 20, "targetReps", "5")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.exercises.length()").value(8));
    }

    @Test
    @DisplayName("WKT-05/04/06 completion: needs a set, duration 1-300, only once")
    void completion() throws Exception {
        String completeUrl = "/api/workouts/" + workoutId + "/complete";
        mvc.perform(postAs(auth, completeUrl, Map.of("durationMinutes", 45)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-05"));

        logSet(auth, workoutId, blockId, 8, 60);
        mvc.perform(postAs(auth, completeUrl, Map.of("durationMinutes", 0)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-04"));
        mvc.perform(postAs(auth, completeUrl, Map.of("durationMinutes", 301)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-04"));
        mvc.perform(postAs(auth, completeUrl, Map.of("durationMinutes", 300)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.durationMinutes").value(300));
        mvc.perform(postAs(auth, completeUrl, Map.of("durationMinutes", 30)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-06"));
    }

    @Test
    @DisplayName("completing without a duration uses the time since the first set started the workout")
    void completionMeasured() throws Exception {
        logSet(auth, workoutId, blockId, 8, 60);
        mvc.perform(post("/api/workouts/" + workoutId + "/complete").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.durationMinutes").value(1));
    }

    @Test
    @DisplayName("WKT-07 a never-started workout needs an explicit duration; WKT-10 start rules")
    void startRules() throws Exception {
        JsonNode custom = createWorkout(auth, Map.of("name", "Empty day"));
        long id = custom.get("id").asLong();
        assertThat(custom.get("exercises").size()).isZero();
        mvc.perform(postAs(auth, "/api/workouts/" + id + "/exercises", Map.of("exerciseId", 15, "targetSets", 3)))
                .andExpect(status().isCreated());
        long block = read(mvc.perform(getAs(auth, "/api/workouts/" + id)).andReturn()).get("exercises").get(0).get("id").asLong();
        // log the set as un-ticked, then tick it: the workout was started by the first set
        mvc.perform(postAs(auth, "/api/workouts/" + id + "/sets", Map.of("workoutExerciseId", block, "reps", 5, "weightKg", 100, "completed", false)))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.completedSets").value(0));
        mvc.perform(post("/api/workouts/" + id + "/start").header("Authorization", auth))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-10"));

        long planned = createWorkout(auth, Map.of("planSlug", "legs")).get("id").asLong();
        mvc.perform(post("/api/workouts/" + planned + "/start").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        long other = createWorkout(auth, Map.of("planSlug", "pull")).get("id").asLong();
        long otherBlock = read(mvc.perform(getAs(auth, "/api/workouts/" + other)).andReturn()).get("exercises").get(0).get("id").asLong();
        jdbc.update("INSERT INTO workout_sets (workout_exercise_id, reps, weight_kg, completed, created_at) VALUES (?, 8, 40, 1, 'now')", otherBlock);
        mvc.perform(post("/api/workouts/" + other + "/complete").header("Authorization", auth))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-07"));
    }

    @Test
    @DisplayName("WKT-09 future-dated workouts can be planned but not completed")
    void futureDate() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        JsonNode future = createWorkout(auth, Map.of("planSlug", "upper", "workoutDate", tomorrow));
        long id = future.get("id").asLong();
        logSet(auth, id, future.get("exercises").get(0).get("id").asLong(), 8, 30);
        mvc.perform(postAs(auth, "/api/workouts/" + id + "/complete", Map.of("durationMinutes", 30)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-09"));
    }

    @Test
    @DisplayName("editing records: set values, tick, workout details; WKT-12 protects completed workouts")
    void editRecords() throws Exception {
        JsonNode afterSet = logSet(auth, workoutId, blockId, 8, 60);
        long setId = afterSet.get("exercises").get(0).get("sets").get(0).get("id").asLong();
        mvc.perform(putAs(auth, setsUrl() + "/" + setId, Map.of("reps", 10, "weightKg", 62.5)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.volume").value(625.0));
        mvc.perform(postAs(auth, "/api/workouts/" + workoutId + "/complete", Map.of("durationMinutes", 50)))
                .andExpect(status().isOk());

        mvc.perform(putAs(auth, setsUrl() + "/" + setId, Map.of("completed", false)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-12"));
        mvc.perform(deleteAs(auth, setsUrl() + "/" + setId))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-12"));

        mvc.perform(putAs(auth, "/api/workouts/" + workoutId,
                        Map.of("name", "Heavy push", "notes", "new PR", "durationMinutes", 55)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Heavy push"))
                .andExpect(jsonPath("$.durationMinutes").value(55));
        mvc.perform(putAs(auth, "/api/workouts/" + workoutId, Map.of("workoutDate", LocalDate.now().plusDays(1).toString())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WKT-09"));
    }

    @Test
    @DisplayName("WKT-11 delete removes the workout with its exercises and sets")
    void deleteCascades() throws Exception {
        logSet(auth, workoutId, blockId, 8, 60);
        mvc.perform(deleteAs(auth, "/api/workouts/" + workoutId)).andExpect(status().isNoContent());
        mvc.perform(getAs(auth, "/api/workouts/" + workoutId)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workout_exercises", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workout_sets", Integer.class)).isZero();
    }

    @Test
    @DisplayName("AUTH-11 another user cannot read, change or delete my workout")
    void otherUserIsolation() throws Exception {
        String intruder = register("intruder");
        mvc.perform(getAs(intruder, "/api/workouts/" + workoutId)).andExpect(status().isNotFound());
        mvc.perform(postAs(intruder, setsUrl(), Map.of("workoutExerciseId", blockId, "reps", 8, "weightKg", 60)))
                .andExpect(status().isNotFound());
        mvc.perform(deleteAs(intruder, "/api/workouts/" + workoutId)).andExpect(status().isNotFound());
        mvc.perform(getAs(intruder, "/api/workouts")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(getAs(auth, "/api/workouts/" + workoutId)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("bad path or query values are 400, unsupported content type keeps its 415")
    void requestErrors() throws Exception {
        mvc.perform(getAs(auth, "/api/workouts/abc")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQ-02"));
        mvc.perform(getAs(auth, "/api/workouts?status=DONE")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/workouts").header("Authorization", auth).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
