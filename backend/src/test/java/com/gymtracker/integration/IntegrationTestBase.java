package com.gymtracker.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Starts the whole application (controllers + services + JdbcTemplate + SQLite file target/gym-test.db)
 * once, and empties the user tables before every test. Seed data (exercises, plan) stays.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper json;

    @BeforeEach
    void cleanUserData() {
        jdbc.update("DELETE FROM workout_sets");
        jdbc.update("DELETE FROM workout_exercises");
        jdbc.update("DELETE FROM workouts");
        jdbc.update("DELETE FROM goals");
        jdbc.update("DELETE FROM auth_tokens");
        jdbc.update("DELETE FROM exercises WHERE owner_id IS NOT NULL");
        jdbc.update("DELETE FROM users");
    }

    /** Registers a user and returns the "Bearer ..." header value. */
    protected String register(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", username, "email", username + "@gym.test", "password", "lift4ever"))))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("register failed: " + result.getResponse().getContentAsString());
        }
        return "Bearer " + read(result).get("token").asText();
    }

    protected String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected MockHttpServletRequestBuilder getAs(String auth, String url) {
        return get(url).header("Authorization", auth);
    }

    protected MockHttpServletRequestBuilder postAs(String auth, String url, Object payload) throws Exception {
        return post(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body(payload));
    }

    protected MockHttpServletRequestBuilder putAs(String auth, String url, Object payload) throws Exception {
        return put(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body(payload));
    }

    protected MockHttpServletRequestBuilder deleteAs(String auth, String url) {
        return delete(url).header("Authorization", auth);
    }

    /** POST /api/workouts {planSlug} and return the created workout JSON. */
    protected JsonNode createWorkout(String auth, Map<String, Object> payload) throws Exception {
        return read(mvc.perform(postAs(auth, "/api/workouts", payload)).andReturn());
    }

    protected JsonNode logSet(String auth, long workoutId, long blockId, int reps, double kg) throws Exception {
        return read(mvc.perform(postAs(auth, "/api/workouts/" + workoutId + "/sets",
                Map.of("workoutExerciseId", blockId, "reps", reps, "weightKg", kg))).andReturn());
    }
}
