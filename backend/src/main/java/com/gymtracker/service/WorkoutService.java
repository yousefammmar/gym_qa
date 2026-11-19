package com.gymtracker.service;

import com.gymtracker.model.WorkoutDtos.AddExerciseRequest;
import com.gymtracker.model.WorkoutDtos.CompleteWorkoutRequest;
import com.gymtracker.model.WorkoutDtos.CreateWorkoutRequest;
import com.gymtracker.model.WorkoutDtos.ExerciseBlock;
import com.gymtracker.model.WorkoutDtos.LogSetRequest;
import com.gymtracker.model.WorkoutDtos.UpdateSetRequest;
import com.gymtracker.model.WorkoutDtos.UpdateWorkoutRequest;
import com.gymtracker.model.WorkoutDtos.WorkoutDetail;
import com.gymtracker.model.WorkoutDtos.WorkoutSummary;
import com.gymtracker.model.PlanDay;
import com.gymtracker.model.PlanExercise;
import com.gymtracker.model.Workout;
import com.gymtracker.model.WorkoutExercise;
import com.gymtracker.model.WorkoutSet;
import com.gymtracker.model.WorkoutStatus;
import com.gymtracker.repository.PlanRepository;
import com.gymtracker.repository.WorkoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Workout sessions: create (optionally from a PPL / Upper / Lower plan day), start, log sets,
 * edit, complete and delete (WKT-01 .. WKT-12).
 *
 * The completion and set-counting rules are static methods (completionMinutes, countValidSets,
 * ensureCompletedKeepsValidSet) so they can be unit tested without a database.
 */
@Service
public class WorkoutService {

    private final WorkoutRepository workouts;
    private final PlanRepository plans;
    private final ExerciseService exercises;
    private final Clock clock;

    public WorkoutService(WorkoutRepository workouts, PlanRepository plans, ExerciseService exercises, Clock clock) {
        this.workouts = workouts;
        this.plans = plans;
        this.exercises = exercises;
        this.clock = clock;
    }

    // ---- create / read / update / delete --------------------------------------------------

    /** WKT-08: a plan day copies its exercises and target sets/reps into the new PLANNED session. */
    @Transactional
    public WorkoutDetail create(long userId, CreateWorkoutRequest req) {
        String slug = req.planSlug() == null ? "" : req.planSlug().trim().toLowerCase();
        PlanDay plan = null;
        if (!slug.isEmpty()) {
            plan = plans.findBySlug(slug)
                    .orElseThrow(() -> ApiException.badRequest("WKT-08", "Unknown plan day: " + req.planSlug()));
        }
        String name = WorkoutValidator.name(req.name(), plan != null ? plan.name() : "Custom workout");
        String notes = WorkoutValidator.notes(req.notes());
        LocalDate date = req.workoutDate() != null ? req.workoutDate() : LocalDate.now(clock);

        long id = workouts.insertWorkout(userId, plan != null ? plan.slug() : null, name, date, notes, now());
        if (plan != null) {
            for (PlanExercise pe : plan.exercises()) {
                workouts.insertWorkoutExercise(id, pe.exerciseId(), pe.targetSets(), pe.targetReps());
            }
        }
        return get(userId, id);
    }

    /** PRG-06: history list, newest first. status == null returns all. */
    // ponytail: two small queries per workout; fine for a local single-user SQLite file
    public List<WorkoutSummary> list(long userId, WorkoutStatus status) {
        List<WorkoutSummary> result = new ArrayList<>();
        for (Workout w : workouts.findWorkouts(userId, status)) {
            List<WorkoutSet> sets = workouts.findSets(w.id());
            result.add(new WorkoutSummary(w.id(), w.name(), w.planSlug(), w.workoutDate(), w.status(),
                    w.durationMinutes(), workouts.findWorkoutExercises(w.id()).size(), countValidSets(sets),
                    ProgressService.round2(sessionVolume(sets))));
        }
        return result;
    }

    public WorkoutDetail get(long userId, long workoutId) {
        Workout w = require(userId, workoutId);
        List<WorkoutSet> sets = workouts.findSets(workoutId);
        List<ExerciseBlock> blocks = new ArrayList<>();
        for (WorkoutExercise we : workouts.findWorkoutExercises(workoutId)) {
            List<WorkoutSet> own = sets.stream().filter(s -> s.workoutExerciseId() == we.id()).toList();
            blocks.add(new ExerciseBlock(we.id(), we.exerciseId(), we.exerciseName(), we.muscleGroup(),
                    we.targetSets(), we.targetReps(), own));
        }
        return new WorkoutDetail(w.id(), w.name(), w.planSlug(), w.workoutDate(), w.status(), w.startedAt(),
                w.completedAt(), w.durationMinutes(), w.notes(), countValidSets(sets),
                ProgressService.round2(sessionVolume(sets)), blocks);
    }

    /** Edit name, date, notes and (WKT-04) duration. WKT-09: a completed workout cannot move into the future. */
    @Transactional
    public WorkoutDetail update(long userId, long workoutId, UpdateWorkoutRequest req) {
        Workout w = require(userId, workoutId);
        String name = WorkoutValidator.name(req.name(), w.name());
        String notes = req.notes() == null ? w.notes() : WorkoutValidator.notes(req.notes());
        LocalDate date = req.workoutDate() != null ? req.workoutDate() : w.workoutDate();
        if (w.status() == WorkoutStatus.COMPLETED && date.isAfter(LocalDate.now(clock))) {
            throw ApiException.badRequest("WKT-09", "A completed workout cannot be dated in the future");
        }
        Integer duration = w.durationMinutes();
        if (req.durationMinutes() != null) {
            WorkoutValidator.requireDuration(req.durationMinutes());
            duration = req.durationMinutes();
        }
        workouts.updateWorkout(workoutId, name, date, notes, duration);
        return get(userId, workoutId);
    }

    /** WKT-11: deletes the workout with its exercises and sets. The UI asks for confirmation first. */
    @Transactional
    public void delete(long userId, long workoutId) {
        require(userId, workoutId);
        workouts.deleteWorkout(workoutId);
    }

    // ---- life cycle ---------------------------------------------------------------------

    /** WKT-10: only a PLANNED workout can be started. */
    @Transactional
    public WorkoutDetail start(long userId, long workoutId) {
        Workout w = require(userId, workoutId);
        if (w.status() != WorkoutStatus.PLANNED) {
            throw ApiException.badRequest("WKT-10", "Only a planned workout can be started");
        }
        workouts.markStarted(workoutId, now());
        return get(userId, workoutId);
    }

    /** WKT-04 .. WKT-07, WKT-09. */
    @Transactional
    public WorkoutDetail complete(long userId, long workoutId, CompleteWorkoutRequest req) {
        Workout w = require(userId, workoutId);
        LocalDateTime now = now();
        int minutes = completionMinutes(w, workouts.findSets(workoutId), req == null ? null : req.durationMinutes(), now);
        workouts.markCompleted(workoutId, now, minutes);
        return get(userId, workoutId);
    }

    /**
     * Decides whether a workout may be completed and returns its duration in minutes.
     * Rules, checked in this order:
     *   WKT-06 not already completed;
     *   WKT-09 not dated in the future;
     *   WKT-05 at least one valid logged set;
     *   WKT-07 duration = the requested minutes, otherwise the time since the workout was started,
     *          otherwise an error (a never-started workout needs an explicit duration);
     *   WKT-04 the duration is 1-300 minutes.
     */
    public static int completionMinutes(Workout w, List<WorkoutSet> sets, Integer requestedMinutes, LocalDateTime now) {
        if (w.status() == WorkoutStatus.COMPLETED) {
            throw ApiException.badRequest("WKT-06", "This workout is already completed");
        }
        if (w.workoutDate().isAfter(now.toLocalDate())) {
            throw ApiException.badRequest("WKT-09", "A workout dated in the future cannot be completed");
        }
        if (countValidSets(sets) == 0) {
            throw ApiException.badRequest("WKT-05", "Log at least one completed set before finishing the workout");
        }
        int minutes;
        if (requestedMinutes != null) {
            minutes = requestedMinutes;
        } else if (w.startedAt() != null) {
            minutes = minutesSince(w.startedAt(), now);
        } else {
            throw ApiException.badRequest("WKT-07", "Enter how many minutes the workout took");
        }
        WorkoutValidator.requireDuration(minutes);
        return minutes;
    }

    /** Whole minutes since start, rounded up, at least 1 (a started workout lasted some time). */
    public static int minutesSince(LocalDateTime start, LocalDateTime now) {
        long seconds = Duration.between(start, now).getSeconds();
        long minutes = (seconds + 59) / 60;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, minutes));
    }

    /** WKT-05: a valid logged set is completed AND has valid reps AND a valid weight. */
    public static int countValidSets(List<WorkoutSet> sets) {
        int count = 0;
        for (WorkoutSet s : sets) {
            if (s.completed() && WorkoutValidator.isValidReps(s.reps()) && WorkoutValidator.isValidWeight(s.weightKg())) {
                count++;
            }
        }
        return count;
    }

    /** kg x reps of the completed sets of one session (shown while logging, before completion). */
    public static double sessionVolume(List<WorkoutSet> sets) {
        double total = 0;
        for (WorkoutSet s : sets) {
            if (s.completed()) {
                total += s.weightKg() * s.reps();
            }
        }
        return total;
    }

    /** WKT-12: an edit may not leave a COMPLETED workout without a valid set. */
    public static void ensureCompletedKeepsValidSet(Workout w, List<WorkoutSet> setsAfterChange) {
        if (w.status() == WorkoutStatus.COMPLETED && countValidSets(setsAfterChange) == 0) {
            throw ApiException.badRequest("WKT-12", "A completed workout must keep at least one completed set");
        }
    }

    // ---- exercises and sets inside a session ----------------------------------------------

    /** Add an exercise (EXR-01 visible to this user) with WKT-03 target sets 1-20. */
    @Transactional
    public WorkoutDetail addExercise(long userId, long workoutId, AddExerciseRequest req) {
        require(userId, workoutId);
        long exerciseId = exercises.requireVisible(userId, req.exerciseId()).id();
        WorkoutValidator.requireSetCount(req.targetSets());
        String targetReps = WorkoutValidator.targetReps(req.targetReps());
        workouts.insertWorkoutExercise(workoutId, exerciseId, req.targetSets(), targetReps);
        return get(userId, workoutId);
    }

    @Transactional
    public WorkoutDetail removeExercise(long userId, long workoutId, long workoutExerciseId) {
        Workout w = require(userId, workoutId);
        requireWorkoutExercise(workoutId, workoutExerciseId);
        List<WorkoutSet> remaining = workouts.findSets(workoutId).stream()
                .filter(s -> s.workoutExerciseId() != workoutExerciseId).toList();
        ensureCompletedKeepsValidSet(w, remaining);
        workouts.deleteWorkoutExercise(workoutExerciseId);
        return get(userId, workoutId);
    }

    /**
     * WKT-01 reps, WKT-02 weight, WKT-03 at most 20 sets per exercise.
     * Logging the first set of a PLANNED workout starts it automatically.
     */
    @Transactional
    public WorkoutDetail logSet(long userId, long workoutId, LogSetRequest req) {
        Workout w = require(userId, workoutId);
        if (req.workoutExerciseId() == null) {
            throw ApiException.badRequest("REQ-03", "Choose which exercise the set belongs to");
        }
        WorkoutExercise we = requireWorkoutExercise(workoutId, req.workoutExerciseId());
        WorkoutValidator.requireReps(req.reps());
        WorkoutValidator.requireWeight(req.weightKg());
        long alreadyLogged = workouts.findSets(workoutId).stream()
                .filter(s -> s.workoutExerciseId() == we.id()).count();
        if (alreadyLogged >= WorkoutValidator.SETS_MAX) {
            throw ApiException.badRequest("WKT-03", "An exercise can have at most 20 sets");
        }
        if (w.status() == WorkoutStatus.PLANNED) {
            workouts.markStarted(workoutId, now());
        }
        boolean completed = req.completed() == null || req.completed();
        workouts.insertSet(we.id(), req.reps(), req.weightKg(), completed, now());
        return get(userId, workoutId);
    }

    /** Edit reps, weight or the completed tick. Missing fields keep their stored value. */
    @Transactional
    public WorkoutDetail updateSet(long userId, long workoutId, long setId, UpdateSetRequest req) {
        Workout w = require(userId, workoutId);
        List<WorkoutSet> sets = workouts.findSets(workoutId);
        WorkoutSet old = sets.stream().filter(s -> s.id() == setId).findFirst()
                .orElseThrow(() -> ApiException.notFound("Set"));
        int reps = req.reps() != null ? req.reps() : old.reps();
        double weight = req.weightKg() != null ? req.weightKg() : old.weightKg();
        boolean completed = req.completed() != null ? req.completed() : old.completed();
        WorkoutValidator.requireReps(reps);
        WorkoutValidator.requireWeight(weight);

        WorkoutSet changed = new WorkoutSet(old.id(), old.workoutExerciseId(), reps, weight, completed);
        List<WorkoutSet> after = sets.stream().map(s -> s.id() == setId ? changed : s).toList();
        ensureCompletedKeepsValidSet(w, after);
        workouts.updateSet(setId, reps, weight, completed);
        return get(userId, workoutId);
    }

    @Transactional
    public WorkoutDetail deleteSet(long userId, long workoutId, long setId) {
        Workout w = require(userId, workoutId);
        List<WorkoutSet> sets = workouts.findSets(workoutId);
        if (sets.stream().noneMatch(s -> s.id() == setId)) {
            throw ApiException.notFound("Set");
        }
        ensureCompletedKeepsValidSet(w, sets.stream().filter(s -> s.id() != setId).toList());
        workouts.deleteSet(setId);
        return get(userId, workoutId);
    }

    // ---- helpers ------------------------------------------------------------------------

    /** AUTH-11: another user's workout looks exactly like a missing one (404). */
    public Workout require(long userId, long workoutId) {
        return workouts.findWorkout(userId, workoutId).orElseThrow(() -> ApiException.notFound("Workout"));
    }

    private WorkoutExercise requireWorkoutExercise(long workoutId, long workoutExerciseId) {
        return workouts.findWorkoutExercises(workoutId).stream()
                .filter(we -> we.id() == workoutExerciseId).findFirst()
                .orElseThrow(() -> ApiException.notFound("Exercise in this workout"));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
