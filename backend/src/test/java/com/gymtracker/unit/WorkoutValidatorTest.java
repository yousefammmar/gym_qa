package com.gymtracker.unit;

import com.gymtracker.service.WorkoutValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.gymtracker.unit.RuleAssert.breaksRule;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundary value analysis of the workout logging limits (WKT-01 .. WKT-04, WKT-08).
 * Each range is tested at min-1, min, min+1, a nominal value, max-1, max, max+1 and "missing" (null).
 */
@DisplayName("Workout limits (WorkoutValidator)")
class WorkoutValidatorTest {

    @ParameterizedTest(name = "reps {0} -> valid = {1}")
    @CsvSource(nullValues = "NULL", value = {
            "-1, false", "0, false", "1, true", "2, true", "50, true", "99, true", "100, true", "101, false", "NULL, false"})
    @DisplayName("WKT-01 repetitions 1-100")
    void repetitionBoundaries(Integer reps, boolean valid) {
        assertThat(WorkoutValidator.isValidReps(reps)).isEqualTo(valid);
        if (valid) {
            WorkoutValidator.requireReps(reps);
        } else {
            breaksRule("WKT-01", () -> WorkoutValidator.requireReps(reps));
        }
    }

    @ParameterizedTest(name = "weight {0} kg -> valid = {1}")
    @CsvSource(nullValues = "NULL", value = {
            "-0.01, false", "0, true", "0.01, true", "250, true", "499.99, true", "500, true", "500.01, false",
            "-100, false", "NULL, false"})
    @DisplayName("WKT-02 weight 0-500 kg")
    void weightBoundaries(Double weight, boolean valid) {
        assertThat(WorkoutValidator.isValidWeight(weight)).isEqualTo(valid);
        if (valid) {
            WorkoutValidator.requireWeight(weight);
        } else {
            breaksRule("WKT-02", () -> WorkoutValidator.requireWeight(weight));
        }
    }

    @Test
    @DisplayName("WKT-02 NaN is not a valid weight")
    void weightNaN() {
        assertThat(WorkoutValidator.isValidWeight(Double.NaN)).isFalse();
    }

    @ParameterizedTest(name = "sets {0} -> valid = {1}")
    @CsvSource(nullValues = "NULL", value = {
            "0, false", "1, true", "2, true", "10, true", "19, true", "20, true", "21, false", "NULL, false"})
    @DisplayName("WKT-03 number of sets 1-20")
    void setCountBoundaries(Integer sets, boolean valid) {
        assertThat(WorkoutValidator.isValidSetCount(sets)).isEqualTo(valid);
        if (valid) {
            WorkoutValidator.requireSetCount(sets);
        } else {
            breaksRule("WKT-03", () -> WorkoutValidator.requireSetCount(sets));
        }
    }

    @ParameterizedTest(name = "duration {0} min -> valid = {1}")
    @CsvSource(nullValues = "NULL", value = {
            "0, false", "1, true", "2, true", "60, true", "299, true", "300, true", "301, false", "-5, false", "NULL, false"})
    @DisplayName("WKT-04 duration 1-300 minutes")
    void durationBoundaries(Integer minutes, boolean valid) {
        assertThat(WorkoutValidator.isValidDuration(minutes)).isEqualTo(valid);
        if (valid) {
            WorkoutValidator.requireDuration(minutes);
        } else {
            breaksRule("WKT-04", () -> WorkoutValidator.requireDuration(minutes));
        }
    }

    @Test
    @DisplayName("WKT-08 name: blank uses fallback, 60 chars ok, 61 rejected")
    void nameRules() {
        assertThat(WorkoutValidator.name("  ", "Push Day")).isEqualTo("Push Day");
        assertThat(WorkoutValidator.name(null, "Push Day")).isEqualTo("Push Day");
        assertThat(WorkoutValidator.name("  Heavy push ", "x")).isEqualTo("Heavy push");
        assertThat(WorkoutValidator.name("n".repeat(60), "x")).hasSize(60);
        breaksRule("WKT-08", () -> WorkoutValidator.name("n".repeat(61), "x"));
    }

    @Test
    @DisplayName("WKT-08 notes max 500, WKT-03 target reps text max 20")
    void textLimits() {
        assertThat(WorkoutValidator.notes(null)).isEmpty();
        assertThat(WorkoutValidator.notes("n".repeat(500))).hasSize(500);
        breaksRule("WKT-08", () -> WorkoutValidator.notes("n".repeat(501)));
        assertThat(WorkoutValidator.targetReps(null)).isEmpty();
        assertThat(WorkoutValidator.targetReps(" 6–10 ")).isEqualTo("6–10");
        assertThat(WorkoutValidator.targetReps("r".repeat(20))).hasSize(20);
        breaksRule("WKT-03", () -> WorkoutValidator.targetReps("r".repeat(21)));
    }
}
