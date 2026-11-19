package com.gymtracker.unit;

import com.gymtracker.model.GoalDtos.GoalRequest;
import com.gymtracker.model.Exercise;
import com.gymtracker.model.Goal;
import com.gymtracker.model.GoalState;
import com.gymtracker.model.GoalStatus;
import com.gymtracker.model.GoalType;
import com.gymtracker.model.MuscleGroup;
import com.gymtracker.model.SetRecord;
import com.gymtracker.model.Workout;
import com.gymtracker.model.WorkoutStatus;
import com.gymtracker.repository.GoalRepository;
import com.gymtracker.repository.WorkoutRepository;
import com.gymtracker.service.ExerciseService;
import com.gymtracker.service.GoalService;
import com.gymtracker.service.GoalValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.gymtracker.model.WorkoutStatus.COMPLETED;
import static com.gymtracker.model.WorkoutStatus.IN_PROGRESS;
import static com.gymtracker.unit.RuleAssert.breaksRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Goal validation, progress and state (GOL-01 .. GOL-10). "Today" is 2026-11-19. */
@DisplayName("Goals (GoalValidator, GoalService)")
class GoalRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("GOL-01 title 1-80 characters")
        void title() {
            breaksRule("GOL-01", () -> GoalValidator.title("   "));
            breaksRule("GOL-01", () -> GoalValidator.title(null));
            assertThat(GoalValidator.title(" x ")).isEqualTo("x");
            assertThat(GoalValidator.title("t".repeat(80))).hasSize(80);
            breaksRule("GOL-01", () -> GoalValidator.title("t".repeat(81)));
        }

        @Test
        @DisplayName("GOL-04 type must be a known value (case-insensitive)")
        void type() {
            assertThat(GoalValidator.type(" workout_count ")).isEqualTo(GoalType.WORKOUT_COUNT);
            breaksRule("GOL-04", () -> GoalValidator.type("lose_weight"));
            breaksRule("GOL-04", () -> GoalValidator.type(null));
        }

        @ParameterizedTest(name = "{0} target {1} -> {2}")
        @CsvSource(nullValues = "NULL", value = {
                "WORKOUT_COUNT, NULL, GOL-02",
                "WORKOUT_COUNT, 0, GOL-02",
                "WORKOUT_COUNT, -1, GOL-02",
                "WORKOUT_COUNT, 1, OK",
                "WORKOUT_COUNT, 10.5, GOL-05",
                "TOTAL_VOLUME, 0.01, OK",
                "TOTAL_VOLUME, 10.5, OK",
                "TOTAL_VOLUME, 1000000, OK",
                "EXERCISE_MAX_WEIGHT, 500, OK",
                "EXERCISE_MAX_WEIGHT, 500.01, GOL-05",
                "EXERCISE_MAX_WEIGHT, 0, GOL-02"})
        @DisplayName("GOL-02 target > 0, GOL-05 type-specific limits")
        void targetValue(GoalType type, Double target, String expected) {
            if (expected.equals("OK")) {
                assertThat(GoalValidator.targetValue(type, target)).isEqualTo(target);
            } else {
                breaksRule(expected, () -> GoalValidator.targetValue(type, target));
            }
        }

        @Test
        @DisplayName("GOL-03 new goal: today or later; yesterday and missing date rejected")
        void targetDateOnCreate() {
            assertThat(GoalValidator.targetDate(TODAY, null, TODAY)).isEqualTo(TODAY);
            assertThat(GoalValidator.targetDate(TODAY.plusDays(1), null, TODAY)).isEqualTo(TODAY.plusDays(1));
            breaksRule("GOL-03", () -> GoalValidator.targetDate(TODAY.minusDays(1), null, TODAY));
            breaksRule("GOL-03", () -> GoalValidator.targetDate(null, null, TODAY));
        }

        /** Cause-effect: (date changed?) AND (date in the past?) -> reject. */
        @Test
        @DisplayName("GOL-03 edited goal: an unchanged past date is kept, a changed past date is rejected")
        void targetDateOnUpdate() {
            LocalDate storedPast = TODAY.minusDays(10);
            assertThat(GoalValidator.targetDate(storedPast, storedPast, TODAY)).isEqualTo(storedPast);   // unchanged, past
            breaksRule("GOL-03", () -> GoalValidator.targetDate(TODAY.minusDays(5), storedPast, TODAY)); // changed, past
            assertThat(GoalValidator.targetDate(TODAY.plusDays(5), storedPast, TODAY)).isEqualTo(TODAY.plusDays(5)); // changed, future
            assertThat(GoalValidator.targetDate(TODAY.plusDays(5), TODAY.plusDays(5), TODAY)).isEqualTo(TODAY.plusDays(5)); // unchanged, future
        }
    }

    @Nested
    @DisplayName("GOL-06 current value is calculated from stored workouts since the start date")
    class CurrentValue {

        private final LocalDate start = LocalDate.of(2026, 11, 10);
        private final List<Workout> workouts = List.of(
                w("2026-11-09", COMPLETED),   // before start
                w("2026-11-10", COMPLETED),   // on start date
                w("2026-11-15", COMPLETED),
                w("2026-11-18", IN_PROGRESS));
        private final List<SetRecord> sets = List.of(
                s("2026-11-09", COMPLETED, 1, 5, 120, true),  // before start
                s("2026-11-10", COMPLETED, 1, 5, 80, true),   // 400
                s("2026-11-15", COMPLETED, 1, 3, 90, true),   // 270
                s("2026-11-15", COMPLETED, 1, 1, 95, false),  // not ticked
                s("2026-11-15", COMPLETED, 2, 10, 100, true), // other exercise, 1000
                s("2026-11-18", IN_PROGRESS, 1, 1, 99, true),  // incomplete workout
                s("2026-11-16", COMPLETED, 1, 8, 70, true));   // lighter set after the heaviest: max must stay 90

        private Goal goal(GoalType type, Long exerciseId) {
            return new Goal(1, 1, "g", type, exerciseId, 100, start, TODAY.plusDays(30), GoalStatus.ACTIVE, null);
        }

        private Workout w(String date, WorkoutStatus status) {
            return new Workout(1, 1, null, "w", LocalDate.parse(date), status, null, null, null, "");
        }

        private SetRecord s(String date, WorkoutStatus status, long exerciseId, int reps, double kg, boolean completed) {
            return new SetRecord(1, LocalDate.parse(date), status, exerciseId, reps, kg, completed);
        }

        @Test
        void workoutCount() {
            assertThat(GoalService.currentValue(goal(GoalType.WORKOUT_COUNT, null), workouts, sets)).isEqualTo(2);
        }

        @Test
        void totalVolume() {
            assertThat(GoalService.currentValue(goal(GoalType.TOTAL_VOLUME, null), workouts, sets)).isEqualTo(2230); // 400+270+1000+560
        }

        @Test
        void exerciseMaxWeight() {
            assertThat(GoalService.currentValue(goal(GoalType.EXERCISE_MAX_WEIGHT, 1L), workouts, sets)).isEqualTo(90);
            assertThat(GoalService.currentValue(goal(GoalType.EXERCISE_MAX_WEIGHT, 3L), workouts, sets)).isZero();
        }

        @Test
        void nothingStoredYet() {
            assertThat(GoalService.currentValue(goal(GoalType.WORKOUT_COUNT, null), List.of(), List.of())).isZero();
            assertThat(GoalService.currentValue(goal(GoalType.TOTAL_VOLUME, null), List.of(), List.of())).isZero();
        }
    }

    @ParameterizedTest(name = "{0} of {1} -> {2}%")
    @CsvSource({"0, 10, 0", "5, 10, 50", "9.99, 10, 99", "10, 10, 100", "15, 10, 100", "1, 3, 33", "2, 3, 66", "5, 0, 0"})
    @DisplayName("GOL-07 percent is rounded down and capped at 100")
    void percent(double current, double target, int expected) {
        assertThat(GoalService.percent(current, target)).isEqualTo(expected);
    }

    /** GOL-10 decision table: status x percent reached x date passed. */
    @ParameterizedTest(name = "{0}, {1}%, due {2} -> {3}")
    @CsvSource({
            "COMPLETED, 100, 2026-11-01, COMPLETED",
            "COMPLETED,  40, 2026-11-01, COMPLETED",
            "ACTIVE,    100, 2026-11-01, ACHIEVED",
            "ACTIVE,    100, 2026-12-01, ACHIEVED",
            "ACTIVE,     99, 2026-11-18, OVERDUE",
            "ACTIVE,     99, 2026-11-19, ACTIVE",
            "ACTIVE,      0, 2026-12-01, ACTIVE"})
    @DisplayName("GOL-10 goal state decision table")
    void state(GoalStatus status, int percent, LocalDate due, GoalState expected) {
        assertThat(GoalService.state(status, percent, due, TODAY)).isEqualTo(expected);
    }

    @Nested
    @DisplayName("GoalService with mocked repositories")
    class WithMocks {

        private final GoalRepository goals = mock(GoalRepository.class);
        private final WorkoutRepository workouts = mock(WorkoutRepository.class);
        private final ExerciseService exercises = mock(ExerciseService.class);
        private final GoalService service = new GoalService(goals, workouts, exercises,
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

        private void stored(GoalStatus status, double target) {
            when(goals.find(1, 5)).thenReturn(Optional.of(new Goal(5, 1, "10 workouts", GoalType.WORKOUT_COUNT, null,
                    target, TODAY.minusDays(7), TODAY.plusDays(7), status, null)));
            when(workouts.findWorkouts(1, null)).thenReturn(List.of(
                    new Workout(1, 1, null, "w", TODAY.minusDays(1), COMPLETED, null, null, 30, "")));
        }

        @Test
        @DisplayName("GOL-08 cannot complete below 100 %")
        void completeTooEarly() {
            stored(GoalStatus.ACTIVE, 2);
            breaksRule("GOL-08", () -> service.complete(1, 5));
            verify(goals, never()).markCompleted(anyLong(), any());
        }

        @Test
        @DisplayName("GOL-08 completes at 100 %, but not twice")
        void completeAtTarget() {
            stored(GoalStatus.ACTIVE, 1);
            assertThat(service.complete(1, 5).id()).isEqualTo(5);
            verify(goals).markCompleted(5, TODAY.atStartOfDay());
            stored(GoalStatus.COMPLETED, 1);
            breaksRule("GOL-08", () -> service.complete(1, 5));
        }

        @Test
        @DisplayName("GOL-09 a completed goal cannot be edited")
        void editCompleted() {
            stored(GoalStatus.COMPLETED, 1);
            breaksRule("GOL-09", () -> service.update(1, 5, new GoalRequest("new", null, null, 3.0, TODAY.plusDays(1))));
        }

        @Test
        @DisplayName("GOL-04 a weight goal needs an exercise the user can see")
        void weightGoalNeedsExercise() {
            breaksRule("GOL-04", () -> service.create(1, new GoalRequest("Bench 100", "EXERCISE_MAX_WEIGHT", null, 100.0, TODAY)));
            when(exercises.requireVisible(1, 1L)).thenReturn(new Exercise(1, "Dumbbell Press", MuscleGroup.CHEST, "", null));
            when(goals.insert(anyLong(), any(), any(), any(), any(Double.class), any(), any(), any(LocalDateTime.class))).thenReturn(9L);
            when(goals.find(1, 9)).thenReturn(Optional.of(new Goal(9, 1, "Bench 100", GoalType.EXERCISE_MAX_WEIGHT, 1L,
                    100, TODAY, TODAY, GoalStatus.ACTIVE, null)));
            assertThat(service.create(1, new GoalRequest("Bench 100", "EXERCISE_MAX_WEIGHT", 1L, 100.0, TODAY)).exerciseName())
                    .isEqualTo("Dumbbell Press");
        }

        @Test
        @DisplayName("other goal types ignore an exerciseId; an ACTIVE goal can be edited")
        void createAndEdit() {
            when(goals.insert(anyLong(), any(), any(), any(), any(Double.class), any(), any(), any(LocalDateTime.class))).thenReturn(5L);
            stored(GoalStatus.ACTIVE, 4);
            service.create(1, new GoalRequest("Four", "WORKOUT_COUNT", 3L, 4.0, TODAY.plusDays(3)));
            verify(goals).insert(1, "Four", GoalType.WORKOUT_COUNT, null, 4.0, TODAY, TODAY.plusDays(3), TODAY.atStartOfDay());

            assertThat(service.update(1, 5, new GoalRequest("Five", null, null, 5.0, TODAY.plusDays(7))).id()).isEqualTo(5);
            verify(goals).update(5, "Five", 5.0, TODAY.plusDays(7));
        }

        @Test
        @DisplayName("list() shows each goal with progress from the stored workouts")
        void listShowsProgress() {
            stored(GoalStatus.ACTIVE, 4);
            Goal goal = goals.find(1, 5).orElseThrow();
            when(goals.findAll(1)).thenReturn(List.of(goal));
            assertThat(service.list(1)).singleElement()
                    .satisfies(view -> {
                        assertThat(view.currentValue()).isEqualTo(1);
                        assertThat(view.percent()).isEqualTo(25);
                        assertThat(view.state()).isEqualTo(GoalState.ACTIVE);
                    });
        }

        @Test
        @DisplayName("unknown goal id -> not found")
        void unknownGoal() {
            when(goals.find(1, 404)).thenReturn(Optional.empty());
            breaksRule("NOT-FOUND", () -> service.delete(1, 404));
        }
    }
}
