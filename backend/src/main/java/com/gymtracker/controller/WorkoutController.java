package com.gymtracker.controller;

import com.gymtracker.model.WorkoutDtos.AddExerciseRequest;
import com.gymtracker.model.WorkoutDtos.CompleteWorkoutRequest;
import com.gymtracker.model.WorkoutDtos.CreateWorkoutRequest;
import com.gymtracker.model.WorkoutDtos.LogSetRequest;
import com.gymtracker.model.WorkoutDtos.UpdateSetRequest;
import com.gymtracker.model.WorkoutDtos.UpdateWorkoutRequest;
import com.gymtracker.model.WorkoutDtos.WorkoutDetail;
import com.gymtracker.model.WorkoutDtos.WorkoutSummary;
import com.gymtracker.model.WorkoutStatus;
import com.gymtracker.service.WorkoutService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** /api/workouts: sessions, their exercises and logged sets. */
@RestController
@RequestMapping("/api/workouts")
public class WorkoutController {

    private final WorkoutService workouts;

    public WorkoutController(WorkoutService workouts) {
        this.workouts = workouts;
    }

    /** ?status=COMPLETED gives the workout history (PRG-06). */
    @GetMapping
    public List<WorkoutSummary> list(@RequestAttribute(AuthInterceptor.USER_ID) long userId,
                                     @RequestParam(required = false) WorkoutStatus status) {
        return workouts.list(userId, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutDetail create(@RequestAttribute(AuthInterceptor.USER_ID) long userId,
                                @RequestBody CreateWorkoutRequest req) {
        return workouts.create(userId, req);
    }

    @GetMapping("/{id}")
    public WorkoutDetail get(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id) {
        return workouts.get(userId, id);
    }

    @PutMapping("/{id}")
    public WorkoutDetail update(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id,
                                @RequestBody UpdateWorkoutRequest req) {
        return workouts.update(userId, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id) {
        workouts.delete(userId, id);
    }

    @PostMapping("/{id}/start")
    public WorkoutDetail start(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id) {
        return workouts.start(userId, id);
    }

    @PostMapping("/{id}/complete")
    public WorkoutDetail complete(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id,
                                  @RequestBody(required = false) CompleteWorkoutRequest req) {
        return workouts.complete(userId, id, req);
    }

    @PostMapping("/{id}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutDetail addExercise(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id,
                                     @RequestBody AddExerciseRequest req) {
        return workouts.addExercise(userId, id, req);
    }

    @DeleteMapping("/{id}/exercises/{workoutExerciseId}")
    public WorkoutDetail removeExercise(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id,
                                        @PathVariable long workoutExerciseId) {
        return workouts.removeExercise(userId, id, workoutExerciseId);
    }

    @PostMapping("/{id}/sets")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutDetail logSet(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id,
                                @RequestBody LogSetRequest req) {
        return workouts.logSet(userId, id, req);
    }

    @PutMapping("/{id}/sets/{setId}")
    public WorkoutDetail updateSet(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id,
                                   @PathVariable long setId, @RequestBody UpdateSetRequest req) {
        return workouts.updateSet(userId, id, setId, req);
    }

    @DeleteMapping("/{id}/sets/{setId}")
    public WorkoutDetail deleteSet(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id,
                                   @PathVariable long setId) {
        return workouts.deleteSet(userId, id, setId);
    }
}
