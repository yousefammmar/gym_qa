package com.gymtracker.unit;

import com.gymtracker.model.ProgressDtos.ExercisePoint;
import com.gymtracker.model.ProgressDtos.WeekStat;
import com.gymtracker.model.SetRecord;
import com.gymtracker.model.Workout;
import com.gymtracker.model.WorkoutStatus;
import com.gymtracker.service.ProgressService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static com.gymtracker.model.WorkoutStatus.COMPLETED;
import static com.gymtracker.model.WorkoutStatus.IN_PROGRESS;
import static com.gymtracker.model.WorkoutStatus.PLANNED;
import static org.assertj.core.api.Assertions.assertThat;

/** Progress calculations with hand-made data (PRG-01 .. PRG-07). "Today" is Thursday 2026-11-19. */
@DisplayName("Progress calculations (ProgressService)")
class ProgressServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    private static Workout workout(long id, String date, WorkoutStatus status) {
        return new Workout(id, 1, null, "w" + id, d(date), status, null, null, null, "");
    }

    private static SetRecord set(String date, WorkoutStatus status, long exerciseId, int reps, double kg, boolean completed) {
        return new SetRecord(1, d(date), status, exerciseId, reps, kg, completed);
    }

    @Nested
    @DisplayName("PRG-02 training volume = sum of weight x reps of completed sets in completed workouts")
    class Volume {

        @Test
        void countsOnlyCompletedSetsOfCompletedWorkouts() {
            List<SetRecord> sets = List.of(
                    set("2026-11-16", COMPLETED, 1, 5, 100, true),     //  500 counted
                    set("2026-11-16", COMPLETED, 2, 10, 60, true),     //  600 counted
                    set("2026-11-16", COMPLETED, 2, 10, 60, false),    //  un-ticked set
                    set("2026-11-18", IN_PROGRESS, 1, 10, 100, true),  //  incomplete workout
                    set("2026-11-19", PLANNED, 1, 10, 100, true));     //  incomplete workout
            assertThat(ProgressService.trainingVolume(sets)).isEqualTo(1100.0);
        }

        @Test
        void emptyAndBodyWeight() {
            assertThat(ProgressService.trainingVolume(List.of())).isZero();
            assertThat(ProgressService.trainingVolume(List.of(set("2026-11-16", COMPLETED, 20, 12, 0, true)))).isZero();
        }

        @Test
        void decimalWeights() {
            assertThat(ProgressService.trainingVolume(List.of(set("2026-11-16", COMPLETED, 1, 3, 22.5, true))))
                    .isEqualTo(67.5);
        }
    }

    @Test
    @DisplayName("PRG-01/07 only COMPLETED workouts are counted")
    void countCompleted() {
        assertThat(ProgressService.countCompleted(List.of())).isZero();
        assertThat(ProgressService.countCompleted(List.of(
                workout(1, "2026-11-16", COMPLETED), workout(2, "2026-11-17", IN_PROGRESS),
                workout(3, "2026-11-18", PLANNED), workout(4, "2026-11-19", COMPLETED)))).isEqualTo(2);
        assertThat(ProgressService.completedDates(List.of(
                workout(1, "2026-11-16", COMPLETED), workout(2, "2026-11-17", PLANNED)))).containsExactly(d("2026-11-16"));
    }

    @Nested
    @DisplayName("PRG-03 streaks")
    class Streaks {

        @Test
        void currentStreakEndingToday() {
            assertThat(ProgressService.currentStreak(List.of(d("2026-11-17"), d("2026-11-18"), d("2026-11-19")), TODAY)).isEqualTo(3);
        }

        @Test
        void currentStreakMayEndYesterday() {
            assertThat(ProgressService.currentStreak(List.of(d("2026-11-16"), d("2026-11-17"), d("2026-11-18")), TODAY)).isEqualTo(3);
        }

        @Test
        void missedYesterdayBreaksTheStreak() {
            assertThat(ProgressService.currentStreak(List.of(d("2026-11-15"), d("2026-11-16"), d("2026-11-17")), TODAY)).isZero();
        }

        @Test
        void currentStreakEdgeCases() {
            assertThat(ProgressService.currentStreak(List.of(), TODAY)).isZero();
            assertThat(ProgressService.currentStreak(List.of(TODAY), TODAY)).isEqualTo(1);
            assertThat(ProgressService.currentStreak(List.of(TODAY, TODAY, d("2026-11-18")), TODAY)).isEqualTo(2); // same day twice
            assertThat(ProgressService.currentStreak(List.of(d("2026-11-17"), TODAY), TODAY)).isEqualTo(1);        // gap on the 18th
        }

        @Test
        void longestStreak() {
            assertThat(ProgressService.longestStreak(List.of())).isZero();
            assertThat(ProgressService.longestStreak(List.of(TODAY))).isEqualTo(1);
            assertThat(ProgressService.longestStreak(List.of(
                    d("2026-11-06"), d("2026-11-01"), d("2026-11-02"), d("2026-11-03"), d("2026-11-05")))).isEqualTo(3); // unordered
            assertThat(ProgressService.longestStreak(List.of(
                    d("2026-11-01"), d("2026-11-01"), d("2026-11-02")))).isEqualTo(2);                                // duplicates
            assertThat(ProgressService.longestStreak(List.of(d("2026-10-31"), d("2026-11-01")))).isEqualTo(2);         // month edge
            assertThat(ProgressService.longestStreak(List.of(
                    d("2026-11-01"), d("2026-11-03"), d("2026-11-04"), d("2026-11-05"), d("2026-11-06")))).isEqualTo(4); // best run last
        }
    }

    @Nested
    @DisplayName("PRG-04 weekly progress")
    class Weekly {

        @Test
        void weekStartsOnMonday() {
            assertThat(ProgressService.weekStart(d("2026-11-16"))).isEqualTo(d("2026-11-16")); // Monday
            assertThat(ProgressService.weekStart(TODAY)).isEqualTo(d("2026-11-16"));         // Thursday
            assertThat(ProgressService.weekStart(d("2026-11-22"))).isEqualTo(d("2026-11-16")); // Sunday
        }

        @ParameterizedTest(name = "{0} of {1} -> {2}%")
        @CsvSource({"0, 5, 0", "1, 5, 20", "2, 3, 66", "5, 5, 100", "6, 5, 100", "3, 0, 0", "3, -1, 0"})
        void weekPercent(int completed, int planned, int expected) {
            assertThat(ProgressService.weekPercent(completed, planned)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "day offset {0} -> index {1}")
        @CsvSource({"-1, -1", "0, 0", "6, 0", "7, 1", "55, 7", "56, -1"})
        void weekIndexBoundaries(int dayOffset, int expected) {
            LocalDate first = d("2026-09-28");
            assertThat(ProgressService.weekIndex(first, first.plusDays(dayOffset), 8)).isEqualTo(expected);
        }

        @Test
        void countCompletedBetweenIncludesBothEnds() {
            List<Workout> list = List.of(
                    workout(1, "2026-11-15", COMPLETED), workout(2, "2026-11-16", COMPLETED),
                    workout(3, "2026-11-22", COMPLETED), workout(4, "2026-11-23", COMPLETED),
                    workout(5, "2026-11-18", IN_PROGRESS));
            assertThat(ProgressService.countCompletedBetween(list, d("2026-11-16"), d("2026-11-22"))).isEqualTo(2);
        }

        @Test
        void eightWeeksOfCountsAndVolume() {
            List<Workout> list = List.of(
                    workout(1, "2026-11-16", COMPLETED), workout(2, "2026-11-19", COMPLETED),
                    workout(3, "2026-11-10", COMPLETED), workout(4, "2026-11-18", IN_PROGRESS),
                    workout(5, "2026-09-27", COMPLETED), workout(6, "2026-09-28", COMPLETED));
            List<SetRecord> sets = List.of(
                    set("2026-11-16", COMPLETED, 1, 5, 100, true),     // week 7: 500
                    set("2026-11-19", COMPLETED, 1, 10, 50, false),    // un-ticked
                    set("2026-11-19", COMPLETED, 1, 10, 40, true),     // week 7: 400
                    set("2026-11-18", IN_PROGRESS, 1, 10, 100, true),  // incomplete workout
                    set("2026-09-28", COMPLETED, 1, 5, 20, true),      // week 0: 100
                    set("2026-09-27", COMPLETED, 1, 10, 100, true));   // before the window

            List<WeekStat> weeks = ProgressService.weeklyStats(list, sets, TODAY, 8);

            assertThat(weeks).hasSize(8);
            assertThat(weeks.get(0).weekStart()).isEqualTo(d("2026-09-28"));
            assertThat(weeks.get(7).weekStart()).isEqualTo(d("2026-11-16"));
            assertThat(weeks).extracting(WeekStat::completedWorkouts).containsExactly(1, 0, 0, 0, 0, 0, 1, 2);
            assertThat(weeks).extracting(WeekStat::volume).containsExactly(100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 900.0);
        }
    }

    @Test
    @DisplayName("PRG-05 exercise progression per completed workout day, in date order")
    void exerciseProgression() {
        List<SetRecord> sets = List.of(
                set("2026-11-16", COMPLETED, 1, 5, 70, true),
                set("2026-11-16", COMPLETED, 1, 3, 75, false),      // not ticked
                set("2026-11-10", COMPLETED, 1, 8, 60, true),
                set("2026-11-10", COMPLETED, 1, 6, 65, true),
                set("2026-11-18", IN_PROGRESS, 1, 5, 80, true),    // incomplete workout
                set("2026-11-16", COMPLETED, 2, 5, 200, true));     // other exercise

        List<ExercisePoint> points = ProgressService.exerciseProgression(sets, 1);

        assertThat(points).containsExactly(
                new ExercisePoint(d("2026-11-10"), 65, 870, 2),
                new ExercisePoint(d("2026-11-16"), 70, 350, 1));
        assertThat(ProgressService.exerciseProgression(sets, 99)).isEmpty();
    }

    @Test
    @DisplayName("round2 rounds half up to 2 decimals")
    void round2() {
        assertThat(ProgressService.round2(1.005 * 1000 / 1000)).isEqualTo(1.0); // binary 1.00499...
        assertThat(ProgressService.round2(12.345678)).isEqualTo(12.35);
        assertThat(ProgressService.round2(0)).isZero();
    }
}
