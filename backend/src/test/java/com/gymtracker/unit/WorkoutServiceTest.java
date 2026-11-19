package com.gymtracker.unit;

import com.gymtracker.model.WorkoutDtos.CompleteWorkoutRequest;
import com.gymtracker.model.WorkoutDtos.CreateWorkoutRequest;
import com.gymtracker.model.WorkoutDtos.LogSetRequest;
import com.gymtracker.model.WorkoutDtos.UpdateSetRequest;
import com.gymtracker.model.WorkoutDtos.UpdateWorkoutRequest;
import com.gymtracker.model.WorkoutDtos.WorkoutDetail;
import com.gymtracker.model.MuscleGroup;
import com.gymtracker.model.PlanDay;
import com.gymtracker.model.PlanExercise;
import com.gymtracker.model.Workout;
import com.gymtracker.model.WorkoutExercise;
import com.gymtracker.model.WorkoutSet;
import com.gymtracker.model.WorkoutStatus;
import com.gymtracker.repository.PlanRepository;
import com.gymtracker.repository.WorkoutRepository;
import com.gymtracker.service.ExerciseService;
import com.gymtracker.service.WorkoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.gymtracker.unit.RuleAssert.breaksRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** WorkoutService decisions that need repository data, with mocked repositories. */
@DisplayName("Workout session rules (WorkoutService with mocks)")
class WorkoutServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 18, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final long USER = 1;
    private static final long WORKOUT = 10;
    private static final WorkoutExercise BLOCK = new WorkoutExercise(100, WORKOUT, 1, "Dumbbell Press", MuscleGroup.CHEST, 1, 3, "6–10");

    private WorkoutRepository workouts;
    private PlanRepository plans;
    private WorkoutService service;

    @BeforeEach
    void setUp() {
        workouts = mock(WorkoutRepository.class);
        plans = mock(PlanRepository.class);
        service = new WorkoutService(workouts, plans, mock(ExerciseService.class),
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        when(workouts.findWorkoutExercises(WORKOUT)).thenReturn(List.of(BLOCK));
    }

    private void storeWorkout(WorkoutStatus status, List<WorkoutSet> sets) {
        when(workouts.findWorkout(USER, WORKOUT)).thenReturn(Optional.of(
                new Workout(WORKOUT, USER, "push", "Push Day", TODAY, status, null, null, null, "")));
        when(workouts.findSets(WORKOUT)).thenReturn(sets);
    }

    private static List<WorkoutSet> sets(int count) {
        List<WorkoutSet> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(new WorkoutSet(i, BLOCK.id(), 8, 60, true));
        }
        return list;
    }

    @Test
    @DisplayName("AUTH-11 another user's workout is reported as not found")
    void otherUsersWorkout() {
        when(workouts.findWorkout(USER, WORKOUT)).thenReturn(Optional.empty());
        breaksRule("NOT-FOUND", () -> service.get(USER, WORKOUT));
    }

    @Test
    @DisplayName("WKT-08 creating from a plan day copies its exercises")
    void createFromPlan() {
        PlanDay push = new PlanDay("push", "Push Day", "Chest", "3 × 6–10", 1, List.of(
                new PlanExercise(1, 1, "Dumbbell Press", MuscleGroup.CHEST, 3, "6–10", ""),
                new PlanExercise(2, 2, "Incline Smith Press", MuscleGroup.CHEST, 3, "6–10", "")));
        when(plans.findBySlug("push")).thenReturn(Optional.of(push));
        when(workouts.insertWorkout(eq(USER), eq("push"), eq("Push Day"), eq(TODAY), eq(""), any())).thenReturn(WORKOUT);
        storeWorkout(WorkoutStatus.PLANNED, List.of());

        WorkoutDetail detail = service.create(USER, new CreateWorkoutRequest(null, " PUSH ", null, null));

        verify(workouts, times(2)).insertWorkoutExercise(eq(WORKOUT), anyLong(), eq(3), eq("6–10"));
        assertThat(detail.status()).isEqualTo(WorkoutStatus.PLANNED);
    }

    @Test
    @DisplayName("WKT-08 unknown plan day is rejected")
    void createUnknownPlan() {
        when(plans.findBySlug("arms")).thenReturn(Optional.empty());
        breaksRule("WKT-08", () -> service.create(USER, new CreateWorkoutRequest(null, "arms", null, null)));
        verify(workouts, never()).insertWorkout(anyLong(), any(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("custom workout (no plan) gets the default name and no exercises")
    void createCustom() {
        when(workouts.insertWorkout(eq(USER), isNull(), eq("Custom workout"), eq(TODAY.minusDays(2)), eq(""), any()))
                .thenReturn(WORKOUT);
        storeWorkout(WorkoutStatus.PLANNED, List.of());
        service.create(USER, new CreateWorkoutRequest("", "", TODAY.minusDays(2), null));
        verify(workouts, never()).insertWorkoutExercise(anyLong(), anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("an empty request creates a custom workout dated today")
    void createWithNothing() {
        when(workouts.insertWorkout(eq(USER), isNull(), eq("Custom workout"), eq(TODAY), eq(""), any())).thenReturn(WORKOUT);
        storeWorkout(WorkoutStatus.PLANNED, List.of());
        assertThat(service.create(USER, new CreateWorkoutRequest(null, null, null, null)).id()).isEqualTo(WORKOUT);
    }

    @Test
    @DisplayName("sets are grouped, counted and limited per exercise block")
    void setsPerBlock() {
        WorkoutExercise squats = new WorkoutExercise(200, WORKOUT, 15, "Squats", MuscleGroup.QUADS, 2, 3, "6–10");
        when(workouts.findWorkoutExercises(WORKOUT)).thenReturn(List.of(BLOCK, squats));
        List<WorkoutSet> mixed = new ArrayList<>();
        mixed.add(new WorkoutSet(99, BLOCK.id(), 8, 60, true));
        for (int i = 1; i <= 20; i++) {
            mixed.add(new WorkoutSet(i, squats.id(), 5, 100, true)); // the squats block is full
        }
        storeWorkout(WorkoutStatus.IN_PROGRESS, mixed);

        WorkoutDetail detail = service.get(USER, WORKOUT);
        assertThat(detail.exercises()).extracting(block -> block.sets().size()).containsExactly(1, 20);
        assertThat(detail.completedSets()).isEqualTo(21);
        assertThat(detail.volume()).isEqualTo(480 + 20 * 500.0);

        assertThat(service.logSet(USER, WORKOUT, new LogSetRequest(BLOCK.id(), 8, 60.0, null))).isNotNull();
        verify(workouts).insertSet(BLOCK.id(), 8, 60.0, true, NOW);
        breaksRule("WKT-03", () -> service.logSet(USER, WORKOUT, new LogSetRequest(squats.id(), 5, 100.0, null)));
    }

    @Test
    @DisplayName("WKT-12 edits on a completed workout are fine while another valid set remains")
    void completedWorkoutWithTwoBlocks() {
        WorkoutExercise squats = new WorkoutExercise(200, WORKOUT, 15, "Squats", MuscleGroup.QUADS, 2, 3, "6–10");
        when(workouts.findWorkoutExercises(WORKOUT)).thenReturn(List.of(BLOCK, squats));
        storeWorkout(WorkoutStatus.COMPLETED, List.of(
                new WorkoutSet(1, BLOCK.id(), 8, 60, true), new WorkoutSet(2, squats.id(), 5, 100, true)));

        assertThat(service.updateSet(USER, WORKOUT, 1, new UpdateSetRequest(null, null, false))).isNotNull();
        verify(workouts).updateSet(1, 8, 60.0, false);
        assertThat(service.removeExercise(USER, WORKOUT, BLOCK.id())).isNotNull();
        verify(workouts).deleteWorkoutExercise(BLOCK.id());
        assertThat(service.deleteSet(USER, WORKOUT, 2)).isNotNull();
        verify(workouts).deleteSet(2);
        breaksRule("NOT-FOUND", () -> service.deleteSet(USER, WORKOUT, 77));
        breaksRule("NOT-FOUND", () -> service.removeExercise(USER, WORKOUT, 777));
    }

    @Test
    @DisplayName("WKT-10 only a PLANNED workout can be started")
    void startRules() {
        storeWorkout(WorkoutStatus.PLANNED, List.of());
        assertThat(service.start(USER, WORKOUT).id()).isEqualTo(WORKOUT);
        verify(workouts).markStarted(WORKOUT, NOW);

        storeWorkout(WorkoutStatus.IN_PROGRESS, List.of());
        breaksRule("WKT-10", () -> service.start(USER, WORKOUT));
        storeWorkout(WorkoutStatus.COMPLETED, sets(1));
        breaksRule("WKT-10", () -> service.start(USER, WORKOUT));
    }

    @Test
    @DisplayName("WKT-03 the 20th set of an exercise is allowed, the 21st is not")
    void setCountLimit() {
        storeWorkout(WorkoutStatus.IN_PROGRESS, sets(19));
        service.logSet(USER, WORKOUT, new LogSetRequest(BLOCK.id(), 8, 60.0, null));
        verify(workouts).insertSet(BLOCK.id(), 8, 60.0, true, NOW);

        storeWorkout(WorkoutStatus.IN_PROGRESS, sets(20));
        breaksRule("WKT-03", () -> service.logSet(USER, WORKOUT, new LogSetRequest(BLOCK.id(), 8, 60.0, null)));
        verify(workouts, times(1)).insertSet(anyLong(), anyInt(), anyDouble(), anyBoolean(), any());
    }

    @Test
    @DisplayName("logging the first set of a PLANNED workout starts it; IN_PROGRESS stays as is")
    void logSetAutoStart() {
        storeWorkout(WorkoutStatus.PLANNED, List.of());
        service.logSet(USER, WORKOUT, new LogSetRequest(BLOCK.id(), 8, 60.0, false));
        verify(workouts).markStarted(WORKOUT, NOW);
        verify(workouts).insertSet(BLOCK.id(), 8, 60.0, false, NOW);

        storeWorkout(WorkoutStatus.IN_PROGRESS, sets(1));
        service.logSet(USER, WORKOUT, new LogSetRequest(BLOCK.id(), 8, 60.0, true));
        verify(workouts, times(1)).markStarted(anyLong(), any());
    }

    @Test
    @DisplayName("logSet validates the exercise, reps and weight before saving")
    void logSetValidation() {
        storeWorkout(WorkoutStatus.IN_PROGRESS, List.of());
        breaksRule("REQ-03", () -> service.logSet(USER, WORKOUT, new LogSetRequest(null, 8, 60.0, true)));
        breaksRule("NOT-FOUND", () -> service.logSet(USER, WORKOUT, new LogSetRequest(999L, 8, 60.0, true)));
        breaksRule("WKT-01", () -> service.logSet(USER, WORKOUT, new LogSetRequest(BLOCK.id(), 0, 60.0, true)));
        breaksRule("WKT-02", () -> service.logSet(USER, WORKOUT, new LogSetRequest(BLOCK.id(), 8, 500.5, true)));
        verify(workouts, never()).insertSet(anyLong(), anyInt(), anyDouble(), anyBoolean(), any());
    }

    @Test
    @DisplayName("WKT-12 un-ticking or deleting the last valid set of a completed workout is refused")
    void completedWorkoutKeepsASet() {
        storeWorkout(WorkoutStatus.COMPLETED, sets(1));
        breaksRule("WKT-12", () -> service.updateSet(USER, WORKOUT, 1, new UpdateSetRequest(null, null, false)));
        breaksRule("WKT-12", () -> service.deleteSet(USER, WORKOUT, 1));
        breaksRule("WKT-12", () -> service.removeExercise(USER, WORKOUT, BLOCK.id()));
        verify(workouts, never()).updateSet(anyLong(), anyInt(), anyDouble(), anyBoolean());
        verify(workouts, never()).deleteSet(anyLong());

        storeWorkout(WorkoutStatus.COMPLETED, sets(2));
        service.deleteSet(USER, WORKOUT, 1);
        verify(workouts).deleteSet(1);
    }

    @Test
    @DisplayName("updateSet keeps fields that are not sent and validates the result")
    void updateSetPartial() {
        storeWorkout(WorkoutStatus.IN_PROGRESS, sets(1));
        service.updateSet(USER, WORKOUT, 1, new UpdateSetRequest(null, 62.5, null));
        verify(workouts).updateSet(1, 8, 62.5, true);
        breaksRule("WKT-01", () -> service.updateSet(USER, WORKOUT, 1, new UpdateSetRequest(101, null, null)));
        breaksRule("WKT-02", () -> service.updateSet(USER, WORKOUT, 1, new UpdateSetRequest(null, 600.0, null)));
        breaksRule("NOT-FOUND", () -> service.updateSet(USER, WORKOUT, 99, new UpdateSetRequest(5, null, null)));
    }

    @Test
    @DisplayName("WKT-09 a completed workout cannot be moved to a future date; WKT-04 edited duration")
    void updateWorkoutRules() {
        storeWorkout(WorkoutStatus.COMPLETED, sets(1));
        breaksRule("WKT-09", () -> service.update(USER, WORKOUT, new UpdateWorkoutRequest(null, TODAY.plusDays(1), null, null)));
        breaksRule("WKT-04", () -> service.update(USER, WORKOUT, new UpdateWorkoutRequest(null, null, null, 0)));
        assertThat(service.update(USER, WORKOUT, new UpdateWorkoutRequest("Heavy push", TODAY.minusDays(1), "felt strong", 70)))
                .isNotNull();
        verify(workouts).updateWorkout(WORKOUT, "Heavy push", TODAY.minusDays(1), "felt strong", 70);

        storeWorkout(WorkoutStatus.PLANNED, List.of());
        service.update(USER, WORKOUT, new UpdateWorkoutRequest(null, TODAY.plusDays(2), null, null));
        verify(workouts).updateWorkout(WORKOUT, "Push Day", TODAY.plusDays(2), "", null);
    }

    @Test
    @DisplayName("complete() stores the duration decided by the completion rules; no body = no duration")
    void completeStoresDuration() {
        storeWorkout(WorkoutStatus.IN_PROGRESS, sets(1));
        assertThat(service.complete(USER, WORKOUT, new CompleteWorkoutRequest(40))).isNotNull();
        verify(workouts).markCompleted(WORKOUT, NOW, 40);
        breaksRule("WKT-07", () -> service.complete(USER, WORKOUT, null)); // never started, no body
    }

    @Test
    @DisplayName("update() keeps stored notes when none are sent")
    void updateKeepsNotes() {
        when(workouts.findWorkout(USER, WORKOUT)).thenReturn(Optional.of(
                new Workout(WORKOUT, USER, null, "Legs", TODAY, WorkoutStatus.PLANNED, null, null, null, "old notes")));
        service.update(USER, WORKOUT, new UpdateWorkoutRequest(null, null, null, null));
        verify(workouts).updateWorkout(WORKOUT, "Legs", TODAY, "old notes", null);
    }
}
