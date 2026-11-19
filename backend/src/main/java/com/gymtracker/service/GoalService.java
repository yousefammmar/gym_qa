package com.gymtracker.service;

import com.gymtracker.model.GoalDtos.GoalRequest;
import com.gymtracker.model.GoalDtos.GoalView;
import com.gymtracker.model.Goal;
import com.gymtracker.model.GoalState;
import com.gymtracker.model.GoalStatus;
import com.gymtracker.model.GoalType;
import com.gymtracker.model.SetRecord;
import com.gymtracker.model.Workout;
import com.gymtracker.model.WorkoutStatus;
import com.gymtracker.repository.GoalRepository;
import com.gymtracker.repository.WorkoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fitness goals (GOL-01 .. GOL-10). A goal stores only its target; the current value is always
 * recalculated from the workouts dated on or after the goal's start date (the day it was created).
 */
@Service
public class GoalService {

    private final GoalRepository goals;
    private final WorkoutRepository workouts;
    private final ExerciseService exercises;
    private final Clock clock;

    public GoalService(GoalRepository goals, WorkoutRepository workouts, ExerciseService exercises, Clock clock) {
        this.goals = goals;
        this.workouts = workouts;
        this.exercises = exercises;
        this.clock = clock;
    }

    public List<GoalView> list(long userId) {
        List<Workout> ws = workouts.findWorkouts(userId, null);
        List<SetRecord> sets = workouts.findSetRecords(userId);
        LocalDate today = LocalDate.now(clock);
        List<GoalView> views = new ArrayList<>();
        for (Goal g : goals.findAll(userId)) {
            views.add(view(userId, g, ws, sets, today));
        }
        return views;
    }

    public GoalView get(long userId, long goalId) {
        Goal g = require(userId, goalId);
        return view(userId, g, workouts.findWorkouts(userId, null), workouts.findSetRecords(userId), LocalDate.now(clock));
    }

    /** GOL-01..05. A weight goal needs an exercise; other types ignore exerciseId. */
    @Transactional
    public GoalView create(long userId, GoalRequest req) {
        LocalDate today = LocalDate.now(clock);
        String title = GoalValidator.title(req.title());
        GoalType type = GoalValidator.type(req.type());
        double target = GoalValidator.targetValue(type, req.targetValue());
        LocalDate targetDate = GoalValidator.targetDate(req.targetDate(), null, today);
        Long exerciseId = null;
        if (type == GoalType.EXERCISE_MAX_WEIGHT) {
            if (req.exerciseId() == null) {
                throw ApiException.badRequest("GOL-04", "Choose the exercise for a weight goal");
            }
            exerciseId = exercises.requireVisible(userId, req.exerciseId()).id();
        }
        long id = goals.insert(userId, title, type, exerciseId, target, today, targetDate, LocalDateTime.now(clock));
        return get(userId, id);
    }

    /** GOL-09: completed goals are read-only. GOL-03: an unchanged past date may stay, a changed one may not. */
    @Transactional
    public GoalView update(long userId, long goalId, GoalRequest req) {
        Goal g = require(userId, goalId);
        if (g.status() == GoalStatus.COMPLETED) {
            throw ApiException.badRequest("GOL-09", "A completed goal can no longer be edited");
        }
        String title = GoalValidator.title(req.title());
        double target = GoalValidator.targetValue(g.type(), req.targetValue());
        LocalDate targetDate = GoalValidator.targetDate(req.targetDate(), g.targetDate(), LocalDate.now(clock));
        goals.update(goalId, title, target, targetDate);
        return get(userId, goalId);
    }

    /** GOL-08: only an ACTIVE goal that has reached 100 % can be marked completed. */
    @Transactional
    public GoalView complete(long userId, long goalId) {
        GoalView current = get(userId, goalId);
        if (current.status() == GoalStatus.COMPLETED) {
            throw ApiException.badRequest("GOL-08", "This goal is already completed");
        }
        if (current.percent() < 100) {
            throw ApiException.badRequest("GOL-08", "Reach the target (100 %) before marking the goal completed");
        }
        goals.markCompleted(goalId, LocalDateTime.now(clock));
        return get(userId, goalId);
    }

    @Transactional
    public void delete(long userId, long goalId) {
        require(userId, goalId);
        goals.delete(goalId);
    }

    // ---- pure calculations ------------------------------------------------------------------

    /** GOL-06: current value from stored data, counting only completed work dated on/after the start date. */
    public static double currentValue(Goal g, List<Workout> ws, List<SetRecord> sets) {
        switch (g.type()) {
            case WORKOUT_COUNT: {
                int count = 0;
                for (Workout w : ws) {
                    if (w.status() == WorkoutStatus.COMPLETED && !w.workoutDate().isBefore(g.startDate())) {
                        count++;
                    }
                }
                return count;
            }
            case TOTAL_VOLUME: {
                List<SetRecord> sinceStart = sets.stream()
                        .filter(s -> !s.workoutDate().isBefore(g.startDate())).toList();
                return ProgressService.round2(ProgressService.trainingVolume(sinceStart));
            }
            default: { // EXERCISE_MAX_WEIGHT
                double best = 0;
                for (SetRecord s : sets) {
                    if (Objects.equals(g.exerciseId(), s.exerciseId()) && s.completed()
                            && s.workoutStatus() == WorkoutStatus.COMPLETED
                            && !s.workoutDate().isBefore(g.startDate()) && s.weightKg() > best) {
                        best = s.weightKg();
                    }
                }
                return best;
            }
        }
    }

    /** GOL-07: whole percent, rounded down (so 100 % really means "reached"), capped at 100. */
    public static int percent(double current, double target) {
        if (target <= 0) {
            return 0;
        }
        return (int) Math.min(100, Math.floor(current * 100 / target));
    }

    /**
     * GOL-10 decision table:
     *   status COMPLETED                         -> COMPLETED
     *   else percent >= 100                      -> ACHIEVED (ready to be marked completed)
     *   else target date before today            -> OVERDUE
     *   else                                     -> ACTIVE
     */
    public static GoalState state(GoalStatus status, int percent, LocalDate targetDate, LocalDate today) {
        if (status == GoalStatus.COMPLETED) {
            return GoalState.COMPLETED;
        }
        if (percent >= 100) {
            return GoalState.ACHIEVED;
        }
        if (targetDate.isBefore(today)) {
            return GoalState.OVERDUE;
        }
        return GoalState.ACTIVE;
    }

    // ---- helpers ----------------------------------------------------------------------------

    private GoalView view(long userId, Goal g, List<Workout> ws, List<SetRecord> sets, LocalDate today) {
        double current = currentValue(g, ws, sets);
        int pct = percent(current, g.targetValue());
        String exerciseName = g.exerciseId() == null ? null : exercises.requireVisible(userId, g.exerciseId()).name();
        return new GoalView(g.id(), g.title(), g.type(), g.exerciseId(), exerciseName, g.targetValue(), current, pct,
                g.status(), state(g.status(), pct, g.targetDate(), today), g.startDate(), g.targetDate(), g.completedAt());
    }

    private Goal require(long userId, long goalId) {
        return goals.find(userId, goalId).orElseThrow(() -> ApiException.notFound("Goal"));
    }
}
