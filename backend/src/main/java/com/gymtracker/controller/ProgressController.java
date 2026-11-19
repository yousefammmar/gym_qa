package com.gymtracker.controller;

import com.gymtracker.model.ProgressDtos.DashboardResponse;
import com.gymtracker.model.ProgressDtos.ExercisePoint;
import com.gymtracker.model.ProgressDtos.ProgressResponse;
import com.gymtracker.model.ProgressDtos.TodayPlan;
import com.gymtracker.model.WorkoutDtos.WorkoutSummary;
import com.gymtracker.model.GoalStatus;
import com.gymtracker.model.WorkoutStatus;
import com.gymtracker.repository.PlanRepository;
import com.gymtracker.service.AuthService;
import com.gymtracker.service.GoalService;
import com.gymtracker.service.ProgressService;
import com.gymtracker.service.WorkoutService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** /api/progress and /api/dashboard (the dashboard just combines the other services). */
@RestController
public class ProgressController {

    private final ProgressService progress;
    private final WorkoutService workouts;
    private final GoalService goals;
    private final AuthService auth;
    private final PlanRepository plans;
    private final Clock clock;

    public ProgressController(ProgressService progress, WorkoutService workouts, GoalService goals,
                              AuthService auth, PlanRepository plans, Clock clock) {
        this.progress = progress;
        this.workouts = workouts;
        this.goals = goals;
        this.auth = auth;
        this.plans = plans;
        this.clock = clock;
    }

    @GetMapping("/api/progress")
    public ProgressResponse progress(@RequestAttribute(AuthInterceptor.USER_ID) long userId) {
        return progress.summary(userId);
    }

    @GetMapping("/api/progress/exercises/{exerciseId}")
    public List<ExercisePoint> exerciseProgress(@RequestAttribute(AuthInterceptor.USER_ID) long userId,
                                                @PathVariable long exerciseId) {
        return progress.exerciseProgress(userId, exerciseId);
    }

    @GetMapping("/api/dashboard")
    public DashboardResponse dashboard(@RequestAttribute(AuthInterceptor.USER_ID) long userId) {
        LocalDate today = LocalDate.now(clock);
        int weekday = today.getDayOfWeek().getValue();
        List<WorkoutSummary> all = workouts.list(userId, null);
        WorkoutSummary active = all.stream().filter(w -> w.status() == WorkoutStatus.IN_PROGRESS).findFirst().orElse(null);
        return new DashboardResponse(
                auth.profile(userId).displayName(),
                progress.summary(userId),
                new TodayPlan(today, weekday, plans.findByWeekday(weekday).orElse(null)),
                active,
                all.stream().limit(5).toList(),
                goals.list(userId).stream().filter(g -> g.status() == GoalStatus.ACTIVE).limit(3).toList());
    }
}
