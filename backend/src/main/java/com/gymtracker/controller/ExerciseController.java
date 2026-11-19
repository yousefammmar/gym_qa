package com.gymtracker.controller;

import com.gymtracker.model.ExerciseRequest;
import com.gymtracker.model.Exercise;
import com.gymtracker.model.MuscleGroup;
import com.gymtracker.model.PlanDay;
import com.gymtracker.repository.PlanRepository;
import com.gymtracker.service.ExerciseService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Exercise library and the (public) weekly plan. */
@RestController
public class ExerciseController {

    private final ExerciseService exercises;
    private final PlanRepository plans;

    public ExerciseController(ExerciseService exercises, PlanRepository plans) {
        this.exercises = exercises;
        this.plans = plans;
    }

    /** Public: the original 5-day PPL + Upper/Lower program with its exercises. */
    @GetMapping("/api/plan")
    public List<PlanDay> plan() {
        return plans.findAll();
    }

    /** Public: values allowed for the muscle-group filter. */
    @GetMapping("/api/exercises/muscle-groups")
    public MuscleGroup[] muscleGroups() {
        return MuscleGroup.values();
    }

    /** e.g. /api/exercises?search=curl&muscleGroup=BICEPS */
    @GetMapping("/api/exercises")
    public List<Exercise> list(@RequestAttribute(AuthInterceptor.USER_ID) long userId,
                               @RequestParam(required = false) String search,
                               @RequestParam(required = false) String muscleGroup) {
        return exercises.list(userId, search, muscleGroup);
    }

    @PostMapping("/api/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    public Exercise create(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @RequestBody ExerciseRequest req) {
        return exercises.create(userId, req);
    }
}
