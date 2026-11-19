package com.gymtracker.controller;

import com.gymtracker.model.GoalDtos.GoalRequest;
import com.gymtracker.model.GoalDtos.GoalView;
import com.gymtracker.service.GoalService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** /api/goals */
@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goals;

    public GoalController(GoalService goals) {
        this.goals = goals;
    }

    @GetMapping
    public List<GoalView> list(@RequestAttribute(AuthInterceptor.USER_ID) long userId) {
        return goals.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalView create(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @RequestBody GoalRequest req) {
        return goals.create(userId, req);
    }

    @PutMapping("/{id}")
    public GoalView update(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id,
                           @RequestBody GoalRequest req) {
        return goals.update(userId, id, req);
    }

    @PostMapping("/{id}/complete")
    public GoalView complete(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id) {
        return goals.complete(userId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id) {
        goals.delete(userId, id);
    }
}
