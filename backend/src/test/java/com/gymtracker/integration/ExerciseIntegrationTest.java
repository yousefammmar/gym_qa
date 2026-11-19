package com.gymtracker.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercise library through the API (EXR-01 .. EXR-07, AUTH-11). */
@DisplayName("Exercise integration")
class ExerciseIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("list, search and filter")
    void searchAndFilter() throws Exception {
        String auth = register("lifter");
        mvc.perform(getAs(auth, "/api/exercises")).andExpect(jsonPath("$.length()").value(27));
        // 4 biceps curls + Leg Curls
        mvc.perform(getAs(auth, "/api/exercises?search=CURL")).andExpect(jsonPath("$.length()").value(5));
        mvc.perform(getAs(auth, "/api/exercises?muscleGroup=core"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].muscleGroup", everyItem(is("CORE"))));
        mvc.perform(getAs(auth, "/api/exercises?search=press&muscleGroup=SHOULDERS")).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(getAs(auth, "/api/exercises?muscleGroup=wings"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EXR-03"));
    }

    @Test
    @DisplayName("custom exercises: created, validated, unique, private to their owner")
    void customExercises() throws Exception {
        String auth = register("lifter");
        String other = register("other");

        mvc.perform(postAs(auth, "/api/exercises", Map.of("name", "Zottman Curl", "muscleGroup", "biceps")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.custom").value(true))
                .andExpect(jsonPath("$.muscleGroup").value("BICEPS"));
        mvc.perform(postAs(auth, "/api/exercises", Map.of("name", "zottman curl", "muscleGroup", "BICEPS")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXR-06"));
        mvc.perform(postAs(auth, "/api/exercises", Map.of("name", "SQUATS", "muscleGroup", "QUADS")))
                .andExpect(status().isConflict());
        mvc.perform(postAs(auth, "/api/exercises", Map.of("name", "X", "muscleGroup", "QUADS")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EXR-04"));
        mvc.perform(postAs(auth, "/api/exercises", Map.of("name", "Sissy Squat")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EXR-05"));

        mvc.perform(getAs(auth, "/api/exercises?search=zottman")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(getAs(other, "/api/exercises?search=zottman")).andExpect(jsonPath("$.length()").value(0));
        // the other user may create the same name for themselves
        mvc.perform(postAs(other, "/api/exercises", Map.of("name", "Zottman Curl", "muscleGroup", "BICEPS")))
                .andExpect(status().isCreated());
    }
}
