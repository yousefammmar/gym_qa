package com.gymtracker.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Authentication through the real HTTP layer and database (AUTH-01 .. AUTH-11). */
@DisplayName("Authentication integration")
class AuthIntegrationTest extends IntegrationTestBase {

    private String registerBody(String username, String email, String password) throws Exception {
        return body(Map.of("username", username, "email", email, "password", password));
    }

    @Test
    @DisplayName("register -> 201 with token; the database holds a BCrypt hash, not the password")
    void registerStoresHash() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("lifter", "Lifter@Gym.test", "lift4ever")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("lifter@gym.test"))
                .andExpect(jsonPath("$.user.displayName").value("lifter"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'lifter'", String.class);
        assertThat(hash).startsWith("$2").doesNotContain("lift4ever");
    }

    @Test
    @DisplayName("register validation errors come back as 400 with the rule ID")
    void registerValidation() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody("ab", "a@b.io", "lift4ever")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH-01"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody("abc", "nope", "lift4ever")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH-02"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody("abc", "a@b.io", "onlyletters")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH-03"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQ-01"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
    }

    @Test
    @DisplayName("AUTH-04 duplicate username (any case) or email -> 409")
    void duplicates() throws Exception {
        register("lifter");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("LIFTER", "other@gym.test", "lift4ever")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUTH-04"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("other", "LIFTER@gym.test", "lift4ever")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUTH-04"));
    }

    @Test
    @DisplayName("login: right password -> token, wrong password or unknown user -> 401")
    void login() throws Exception {
        register("lifter");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "lifter", "password", "lift4ever"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "lifter", "password", "wrong1234"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-06"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "ghost", "password", "lift4ever"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Wrong username or password"));
    }

    @Test
    @DisplayName("AUTH-08 protected endpoints need a valid token; public plan does not")
    void protectedEndpoints() throws Exception {
        mvc.perform(get("/api/workouts")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-08"));
        mvc.perform(getAs("Bearer made-up", "/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/plan")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));
        mvc.perform(get("/api/exercises/muscle-groups")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUTH-07 logout deletes the token; AUTH-08 an expired token is refused")
    void logoutAndExpiry() throws Exception {
        String auth = register("lifter");
        mvc.perform(getAs(auth, "/api/profile")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", auth)).andExpect(status().isNoContent());
        mvc.perform(getAs(auth, "/api/profile")).andExpect(status().isUnauthorized());

        String second = register("lifter2");
        jdbc.update("UPDATE auth_tokens SET created_at = ?", LocalDateTime.now().minusDays(8).toString());
        mvc.perform(getAs(second, "/api/profile")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Your session expired, please log in again"));
    }

    @Test
    @DisplayName("profile update and password change (AUTH-09, AUTH-10)")
    void profile() throws Exception {
        String auth = register("lifter");
        register("other");
        mvc.perform(putAs(auth, "/api/profile", Map.of("displayName", "Big Lifter", "email", "new@gym.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Big Lifter"));
        mvc.perform(putAs(auth, "/api/profile", Map.of("displayName", "x", "email", "other@gym.test")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUTH-04"));

        mvc.perform(putAs(auth, "/api/profile/password", Map.of("currentPassword", "nope1234", "newPassword", "stronger99")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH-10"));
        mvc.perform(putAs(auth, "/api/profile/password", Map.of("currentPassword", "lift4ever", "newPassword", "stronger99")))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "lifter", "password", "stronger99"))))
                .andExpect(status().isOk());
        mvc.perform(getAs(auth, "/api/profile")).andExpect(status().isOk()); // the session that changed it stays valid
    }
}
