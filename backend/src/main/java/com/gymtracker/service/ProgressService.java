package com.gymtracker.service;

import com.gymtracker.model.ProgressDtos.ExercisePoint;
import com.gymtracker.model.ProgressDtos.ProgressResponse;
import com.gymtracker.model.ProgressDtos.ThisWeek;
import com.gymtracker.model.ProgressDtos.WeekStat;
import com.gymtracker.model.SetRecord;
import com.gymtracker.model.Workout;
import com.gymtracker.model.WorkoutStatus;
import com.gymtracker.repository.PlanRepository;
import com.gymtracker.repository.WorkoutRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Progress statistics (PRG-01 .. PRG-07). All numbers come from the stored workouts and sets.
 * Incomplete workouts (PLANNED, IN_PROGRESS) never count (PRG-07).
 *
 * The calculations are static methods that take plain lists, so unit tests can feed them
 * hand-made data and a fixed "today".
 */
@Service
public class ProgressService {

    /** How many weeks the weekly chart shows (this week + 7 before it). */
    public static final int WEEKS_SHOWN = 8;

    private final WorkoutRepository workouts;
    private final PlanRepository plans;
    private final Clock clock;

    public ProgressService(WorkoutRepository workouts, PlanRepository plans, Clock clock) {
        this.workouts = workouts;
        this.plans = plans;
        this.clock = clock;
    }

    public ProgressResponse summary(long userId) {
        List<Workout> all = workouts.findWorkouts(userId, null);
        List<SetRecord> sets = workouts.findSetRecords(userId);
        LocalDate today = LocalDate.now(clock);
        List<LocalDate> dates = completedDates(all);

        LocalDate monday = weekStart(today);
        int doneThisWeek = countCompletedBetween(all, monday, monday.plusDays(6));
        int planned = plans.findAll().size();

        return new ProgressResponse(
                countCompleted(all),
                round2(trainingVolume(sets)),
                currentStreak(dates, today),
                longestStreak(dates),
                new ThisWeek(monday, doneThisWeek, planned, weekPercent(doneThisWeek, planned)),
                weeklyStats(all, sets, today, WEEKS_SHOWN));
    }

    /** PRG-05 */
    public List<ExercisePoint> exerciseProgress(long userId, long exerciseId) {
        return exerciseProgression(workouts.findSetRecords(userId), exerciseId);
    }

    // ---- pure calculations ------------------------------------------------------------------

    /** PRG-01: only COMPLETED workouts are counted. */
    public static int countCompleted(List<Workout> list) {
        int count = 0;
        for (Workout w : list) {
            if (w.status() == WorkoutStatus.COMPLETED) {
                count++;
            }
        }
        return count;
    }

    /** PRG-02: training volume = sum of weight x reps over completed sets of completed workouts. */
    public static double trainingVolume(List<SetRecord> sets) {
        double total = 0;
        for (SetRecord s : sets) {
            if (s.completed() && s.workoutStatus() == WorkoutStatus.COMPLETED) {
                total += s.weightKg() * s.reps();
            }
        }
        return total;
    }

    public static List<LocalDate> completedDates(List<Workout> list) {
        List<LocalDate> dates = new ArrayList<>();
        for (Workout w : list) {
            if (w.status() == WorkoutStatus.COMPLETED) {
                dates.add(w.workoutDate());
            }
        }
        return dates;
    }

    /**
     * PRG-03: number of consecutive calendar days, each with at least one completed workout,
     * ending today. If nothing was done today yet the streak may still end yesterday
     * (it is not broken until a whole day is missed).
     */
    public static int currentStreak(Collection<LocalDate> completedDates, LocalDate today) {
        Set<LocalDate> days = new HashSet<>(completedDates);
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** PRG-03: longest run of consecutive training days ever. Several workouts on one day count once. */
    public static int longestStreak(Collection<LocalDate> completedDates) {
        int best = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : new TreeSet<>(completedDates)) {
            if (previous != null && day.equals(previous.plusDays(1))) {
                run++;
            } else {
                run = 1;
            }
            if (run > best) {
                best = run;
            }
            previous = day;
        }
        return best;
    }

    /** Weeks run Monday to Sunday (matches the plan: Mon = Push ... Sun = rest). */
    public static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Completed workouts dated from..to, both ends included. */
    public static int countCompletedBetween(List<Workout> list, LocalDate from, LocalDate to) {
        int count = 0;
        for (Workout w : list) {
            if (w.status() == WorkoutStatus.COMPLETED
                    && !w.workoutDate().isBefore(from) && !w.workoutDate().isAfter(to)) {
                count++;
            }
        }
        return count;
    }

    /** PRG-04: share of the planned training days done this week, whole percent, never above 100. */
    public static int weekPercent(int completed, int planned) {
        if (planned <= 0) {
            return 0;
        }
        return Math.min(100, completed * 100 / planned);
    }

    /**
     * PRG-04: completed workouts and volume for each of the last {@code weeks} weeks, oldest first.
     * The last entry is the current week.
     */
    public static List<WeekStat> weeklyStats(List<Workout> list, List<SetRecord> sets, LocalDate today, int weeks) {
        LocalDate first = weekStart(today).minusWeeks(weeks - 1L);
        int[] counts = new int[weeks];
        double[] volumes = new double[weeks];

        for (Workout w : list) {
            int index = weekIndex(first, w.workoutDate(), weeks);
            if (w.status() == WorkoutStatus.COMPLETED && index >= 0) {
                counts[index]++;
            }
        }
        for (SetRecord s : sets) {
            int index = weekIndex(first, s.workoutDate(), weeks);
            if (s.completed() && s.workoutStatus() == WorkoutStatus.COMPLETED && index >= 0) {
                volumes[index] += s.weightKg() * s.reps();
            }
        }

        List<WeekStat> result = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            result.add(new WeekStat(first.plusWeeks(i), counts[i], round2(volumes[i])));
        }
        return result;
    }

    /** Position of date in the window that starts on Monday {@code first}; -1 when outside it. */
    public static int weekIndex(LocalDate first, LocalDate date, int weeks) {
        long days = ChronoUnit.DAYS.between(first, date);
        if (days < 0) {
            return -1;
        }
        long index = days / 7;
        return index < weeks ? (int) index : -1;
    }

    /** PRG-05: per completed workout day: heaviest set, volume and number of completed sets for one exercise. */
    public static List<ExercisePoint> exerciseProgression(List<SetRecord> sets, long exerciseId) {
        Map<LocalDate, double[]> byDay = new TreeMap<>(); // [maxWeight, volume, setCount]
        for (SetRecord s : sets) {
            if (s.exerciseId() == exerciseId && s.completed() && s.workoutStatus() == WorkoutStatus.COMPLETED) {
                double[] acc = byDay.computeIfAbsent(s.workoutDate(), d -> new double[3]);
                acc[0] = Math.max(acc[0], s.weightKg());
                acc[1] += s.weightKg() * s.reps();
                acc[2]++;
            }
        }
        List<ExercisePoint> points = new ArrayList<>();
        byDay.forEach((day, acc) -> points.add(new ExercisePoint(day, acc[0], round2(acc[1]), (int) acc[2])));
        return points;
    }

    /** Rounds to 2 decimals for display (2.5 kg plates give exact values anyway). */
    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
