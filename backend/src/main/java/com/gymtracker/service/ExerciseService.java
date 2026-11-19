package com.gymtracker.service;

import com.gymtracker.model.ExerciseRequest;
import com.gymtracker.model.Exercise;
import com.gymtracker.model.MuscleGroup;
import com.gymtracker.repository.ExerciseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Exercise library: list, search, filter, create custom (EXR-01 .. EXR-07). */
@Service
public class ExerciseService {

    private final ExerciseRepository exercises;

    public ExerciseService(ExerciseRepository exercises) {
        this.exercises = exercises;
    }

    /** EXR-01..03: built-in + own custom exercises, optionally searched and filtered. */
    public List<Exercise> list(long userId, String search, String muscleGroup) {
        MuscleGroup group = ExerciseValidator.muscleGroup(muscleGroup, false);
        return filter(exercises.findVisible(userId), search, group);
    }

    /**
     * EXR-02 + EXR-03. Keeps an exercise when it matches the muscle group (if one is given) AND
     * the search text (if any) appears in its name OR description, ignoring case and surrounding spaces.
     */
    public static List<Exercise> filter(List<Exercise> all, String search, MuscleGroup group) {
        String term = search == null ? "" : search.trim().toLowerCase();
        List<Exercise> result = new ArrayList<>();
        for (Exercise e : all) {
            boolean groupMatches = group == null || e.muscleGroup() == group;
            boolean textMatches = term.isEmpty()
                    || e.name().toLowerCase().contains(term)
                    || e.description().toLowerCase().contains(term);
            if (groupMatches && textMatches) {
                result.add(e);
            }
        }
        return result;
    }

    /** EXR-04..07: valid input, and the name must not already exist among the exercises this user sees. */
    @Transactional
    public Exercise create(long userId, ExerciseRequest req) {
        String name = ExerciseValidator.name(req.name());
        MuscleGroup group = ExerciseValidator.muscleGroup(req.muscleGroup(), true);
        String description = ExerciseValidator.description(req.description());
        boolean duplicate = exercises.findVisible(userId).stream().anyMatch(e -> e.name().equalsIgnoreCase(name));
        if (duplicate) {
            throw ApiException.conflict("EXR-06", "An exercise called \"" + name + "\" already exists");
        }
        long id = exercises.insert(name, group, description, userId);
        return requireVisible(userId, id);
    }

    /** AUTH-11: another user's custom exercise behaves as if it did not exist. */
    public Exercise requireVisible(long userId, Long exerciseId) {
        if (exerciseId == null) {
            throw ApiException.badRequest("EXR-01", "Choose an exercise");
        }
        return exercises.findVisibleById(userId, exerciseId).orElseThrow(() -> ApiException.notFound("Exercise"));
    }
}
