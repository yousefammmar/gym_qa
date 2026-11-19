package com.gymtracker.unit;

import com.gymtracker.model.Workout;
import com.gymtracker.model.WorkoutSet;
import com.gymtracker.model.WorkoutStatus;
import com.gymtracker.service.WorkoutService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.gymtracker.unit.RuleAssert.breaksRule;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Workout completion rules as a decision table (WorkoutService.completionMinutes), plus the
 * "valid logged set" condition (countValidSets) and WKT-12.
 *
 * Decision table (see docs/test-plan.md, DT-1):
 *   R1 status COMPLETED                                   -> WKT-06
 *   R2 not completed, dated in the future                 -> WKT-09
 *   R3 ... no valid set                                   -> WKT-05
 *   R4 ... valid set, minutes given, 1-300                -> minutes
 *   R5 ... valid set, minutes given, out of range         -> WKT-04
 *   R6 ... valid set, no minutes, started, elapsed <= 300 -> elapsed
 *   R7 ... valid set, no minutes, started, elapsed > 300  -> WKT-04
 *   R8 ... valid set, no minutes, never started           -> WKT-07
 */
@DisplayName("Workout completion rules")
class WorkoutCompletionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 18, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final List<WorkoutSet> ONE_VALID_SET = List.of(new WorkoutSet(1, 1, 8, 60, true));

    private static Workout workout(WorkoutStatus status, LocalDate date, LocalDateTime startedAt) {
        return new Workout(1, 1, "push", "Push Day", date, status, startedAt, null, null, "");
    }

    @Test
    @DisplayName("R1 an already completed workout cannot be completed again")
    void r1AlreadyCompleted() {
        breaksRule("WKT-06", () -> WorkoutService.completionMinutes(
                workout(WorkoutStatus.COMPLETED, TODAY, NOW.minusHours(1)), ONE_VALID_SET, 60, NOW));
    }

    @Test
    @DisplayName("R2 a workout dated tomorrow cannot be completed; today and yesterday can")
    void r2FutureDate() {
        breaksRule("WKT-09", () -> WorkoutService.completionMinutes(
                workout(WorkoutStatus.PLANNED, TODAY.plusDays(1), null), ONE_VALID_SET, 60, NOW));
        assertThat(WorkoutService.completionMinutes(workout(WorkoutStatus.PLANNED, TODAY, null), ONE_VALID_SET, 60, NOW))
                .isEqualTo(60);
        assertThat(WorkoutService.completionMinutes(workout(WorkoutStatus.PLANNED, TODAY.minusDays(1), null), ONE_VALID_SET, 60, NOW))
                .isEqualTo(60);
    }

    @Test
    @DisplayName("R3 WKT-05 no sets, or only un-ticked sets -> cannot complete")
    void r3NoValidSet() {
        Workout w = workout(WorkoutStatus.IN_PROGRESS, TODAY, NOW.minusMinutes(30));
        breaksRule("WKT-05", () -> WorkoutService.completionMinutes(w, List.of(), 30, NOW));
        breaksRule("WKT-05", () -> WorkoutService.completionMinutes(w, List.of(new WorkoutSet(1, 1, 8, 60, false)), 30, NOW));
    }

    @ParameterizedTest(name = "R4/R5 requested {0} min -> {1}")
    @CsvSource({"1, OK", "300, OK", "45, OK", "0, WKT-04", "301, WKT-04", "-10, WKT-04"})
    @DisplayName("R4/R5 the requested duration is used when it is 1-300")
    void r4r5RequestedMinutes(int requested, String expected) {
        Workout w = workout(WorkoutStatus.IN_PROGRESS, TODAY, NOW.minusMinutes(30));
        if (expected.equals("OK")) {
            // the requested value wins over the 30 minutes measured since start
            assertThat(WorkoutService.completionMinutes(w, ONE_VALID_SET, requested, NOW)).isEqualTo(requested);
        } else {
            breaksRule(expected, () -> WorkoutService.completionMinutes(w, ONE_VALID_SET, requested, NOW));
        }
    }

    @Test
    @DisplayName("R6 no duration given: time since Start is used")
    void r6MeasuredFromStart() {
        Workout w = workout(WorkoutStatus.IN_PROGRESS, TODAY, NOW.minusMinutes(52));
        assertThat(WorkoutService.completionMinutes(w, ONE_VALID_SET, null, NOW)).isEqualTo(52);
    }

    @Test
    @DisplayName("R7 started more than 300 minutes ago and no duration given -> WKT-04")
    void r7MeasuredTooLong() {
        Workout exactly300 = workout(WorkoutStatus.IN_PROGRESS, TODAY, NOW.minusMinutes(300));
        assertThat(WorkoutService.completionMinutes(exactly300, ONE_VALID_SET, null, NOW)).isEqualTo(300);
        Workout over = workout(WorkoutStatus.IN_PROGRESS, TODAY, NOW.minusMinutes(300).minusSeconds(1));
        breaksRule("WKT-04", () -> WorkoutService.completionMinutes(over, ONE_VALID_SET, null, NOW));
    }

    @Test
    @DisplayName("R8 never started and no duration given -> WKT-07")
    void r8NeverStarted() {
        breaksRule("WKT-07", () -> WorkoutService.completionMinutes(
                workout(WorkoutStatus.PLANNED, TODAY, null), ONE_VALID_SET, null, NOW));
    }

    @Test
    @DisplayName("rules are checked in order: completed beats future date beats missing sets")
    void ruleOrder() {
        breaksRule("WKT-06", () -> WorkoutService.completionMinutes(
                workout(WorkoutStatus.COMPLETED, TODAY.plusDays(3), null), List.of(), null, NOW));
        breaksRule("WKT-09", () -> WorkoutService.completionMinutes(
                workout(WorkoutStatus.PLANNED, TODAY.plusDays(3), null), List.of(), null, NOW));
    }

    @ParameterizedTest(name = "elapsed {0} s -> {1} min")
    @CsvSource({"0, 1", "1, 1", "59, 1", "60, 1", "61, 2", "2700, 45", "-30, 1"})
    @DisplayName("minutesSince rounds up and is at least 1")
    void minutesSince(long seconds, int expected) {
        assertThat(WorkoutService.minutesSince(NOW.minusSeconds(seconds), NOW)).isEqualTo(expected);
    }

    /** Multiple condition coverage: completed x valid reps x valid weight (8 combinations). */
    @ParameterizedTest(name = "completed={0}, reps={1}, weight={2} -> counted={3}")
    @CsvSource({
            "true,  8,   60, 1",
            "true,  8,  600, 0",
            "true,  0,   60, 0",
            "true,  0,  600, 0",
            "false, 8,   60, 0",
            "false, 8,  600, 0",
            "false, 0,   60, 0",
            "false, 0,  600, 0"})
    @DisplayName("WKT-05 a valid logged set is completed AND valid reps AND valid weight")
    void validSetConditions(boolean completed, int reps, double weight, int counted) {
        assertThat(WorkoutService.countValidSets(List.of(new WorkoutSet(1, 1, reps, weight, completed)))).isEqualTo(counted);
    }

    @Test
    @DisplayName("countValidSets loop: zero, one and many iterations")
    void countValidSetsLoop() {
        assertThat(WorkoutService.countValidSets(List.of())).isZero();
        assertThat(WorkoutService.countValidSets(List.of(
                new WorkoutSet(1, 1, 8, 60, true),
                new WorkoutSet(2, 1, 8, 60, false),
                new WorkoutSet(3, 2, 10, 0, true)))).isEqualTo(2);
    }

    @Test
    @DisplayName("session volume counts ticked sets only")
    void sessionVolume() {
        assertThat(WorkoutService.sessionVolume(List.of(
                new WorkoutSet(1, 1, 8, 60, true),      // 480
                new WorkoutSet(2, 1, 10, 50, false),    // ignored
                new WorkoutSet(3, 2, 12, 2.5, true))))  // 30
                .isEqualTo(510.0);
        assertThat(WorkoutService.sessionVolume(List.of())).isZero();
    }

    @Test
    @DisplayName("WKT-12 a completed workout must keep at least one valid set")
    void completedKeepsValidSet() {
        Workout completed = workout(WorkoutStatus.COMPLETED, TODAY, null);
        Workout inProgress = workout(WorkoutStatus.IN_PROGRESS, TODAY, null);
        breaksRule("WKT-12", () -> WorkoutService.ensureCompletedKeepsValidSet(completed, List.of()));
        breaksRule("WKT-12", () -> WorkoutService.ensureCompletedKeepsValidSet(completed,
                List.of(new WorkoutSet(1, 1, 8, 60, false))));
        WorkoutService.ensureCompletedKeepsValidSet(completed, ONE_VALID_SET);   // no exception
        WorkoutService.ensureCompletedKeepsValidSet(inProgress, List.of());      // no exception
    }
}
