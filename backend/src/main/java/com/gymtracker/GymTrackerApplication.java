package com.gymtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

/**
 * Starts the Gym Tracker: REST API under /api and the website from ../frontend,
 * both on http://localhost:8080. Run from the backend/ folder with: ./mvnw spring-boot:run
 */
@SpringBootApplication
public class GymTrackerApplication {

    public static void main(String[] args) throws IOException {
        // SQLite creates the database file but not its folder, so make ./data first.
        Files.createDirectories(Path.of("data"));
        SpringApplication.run(GymTrackerApplication.class, args);
    }

    /** "Now" for every service. Tests replace it with Clock.fixed(...) to control dates. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /** AUTH-05: passwords are stored as BCrypt hashes. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
