package com.gymtracker.unit;

import com.gymtracker.model.ExerciseRequest;
import com.gymtracker.model.Exercise;
import com.gymtracker.model.MuscleGroup;
import com.gymtracker.repository.ExerciseRepository;
import com.gymtracker.service.ExerciseService;
import com.gymtracker.service.ExerciseValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static com.gymtracker.model.MuscleGroup.BACK;
import static com.gymtracker.model.MuscleGroup.BICEPS;
import static com.gymtracker.model.MuscleGroup.CHEST;
import static com.gymtracker.model.MuscleGroup.QUADS;
import static com.gymtracker.model.MuscleGroup.SHOULDERS;
import static com.gymtracker.unit.RuleAssert.breaksRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercise filtering (EXR-02, EXR-03) and custom exercise validation (EXR-04 .. EXR-07). */
@DisplayName("Exercise library (ExerciseService, ExerciseValidator)")
class ExerciseServiceTest {

    private static final List<Exercise> LIBRARY = List.of(
            new Exercise(1, "Dumbbell Press", CHEST, "Chest press", null),
            new Exercise(4, "Smith Machine Shoulder Press", SHOULDERS, "Shoulders", null),
            new Exercise(9, "Wide Grip Row", BACK, "Row", null),
            new Exercise(14, "Spider Curl", BICEPS, "Biceps", null),
            new Exercise(15, "Squats", QUADS, "Compound", null),
            new Exercise(27, "Back Extension", BACK, "Lower back", null),
            new Exercise(40, "Zottman Curl", BICEPS, "My forearm finisher", 7L));

    private static List<String> names(List<Exercise> list) {
        return list.stream().map(Exercise::name).toList();
    }

    /** Equivalence classes of (search given?, group given?) and where the text matches. */
    @ParameterizedTest(name = "search=\"{0}\", group={1} -> {2}")
    @CsvSource(nullValues = "NULL", delimiter = '|', value = {
            "NULL   | NULL      | 7",
            "''     | NULL      | 7",
            "curl   | NULL      | 2",
            "CURL   | NULL      | 2",
            "'  row '| NULL     | 1",
            "lower  | NULL      | 1",
            "NULL   | BACK      | 2",
            "press  | SHOULDERS | 1",
            "press  | BICEPS    | 0",
            "zzz    | NULL      | 0"})
    void filterCombinations(String search, MuscleGroup group, int expectedCount) {
        assertThat(ExerciseService.filter(LIBRARY, search, group)).hasSize(expectedCount);
    }

    @Test
    @DisplayName("EXR-02 search looks at name OR description; EXR-03 group AND search must both match")
    void filterResults() {
        assertThat(names(ExerciseService.filter(LIBRARY, "lower", null))).containsExactly("Back Extension"); // description only
        assertThat(names(ExerciseService.filter(LIBRARY, "curl", BICEPS))).containsExactly("Spider Curl", "Zottman Curl");
        assertThat(names(ExerciseService.filter(LIBRARY, null, BACK))).containsExactly("Wide Grip Row", "Back Extension");
        assertThat(ExerciseService.filter(List.of(), "curl", BICEPS)).isEmpty();
    }

    @Test
    @DisplayName("EXR-04 name 2-50 characters")
    void nameBoundaries() {
        breaksRule("EXR-04", () -> ExerciseValidator.name("A"));
        assertThat(ExerciseValidator.name(" Ab ")).isEqualTo("Ab");
        assertThat(ExerciseValidator.name("n".repeat(50))).hasSize(50);
        breaksRule("EXR-04", () -> ExerciseValidator.name("n".repeat(51)));
        breaksRule("EXR-04", () -> ExerciseValidator.name(null));
    }

    @Test
    @DisplayName("EXR-05 / EXR-03 muscle group parsing")
    void muscleGroup() {
        assertThat(ExerciseValidator.muscleGroup("biceps", true)).isEqualTo(BICEPS);
        assertThat(ExerciseValidator.muscleGroup(" Full_Body ", false)).isEqualTo(MuscleGroup.FULL_BODY);
        assertThat(ExerciseValidator.muscleGroup("", false)).isNull();
        assertThat(ExerciseValidator.muscleGroup(null, false)).isNull();
        breaksRule("EXR-05", () -> ExerciseValidator.muscleGroup("", true));
        breaksRule("EXR-05", () -> ExerciseValidator.muscleGroup("wings", true));
        breaksRule("EXR-03", () -> ExerciseValidator.muscleGroup("wings", false));
    }

    @Test
    @DisplayName("EXR-07 description max 200")
    void description() {
        assertThat(ExerciseValidator.description(null)).isEmpty();
        assertThat(ExerciseValidator.description("d".repeat(200))).hasSize(200);
        breaksRule("EXR-07", () -> ExerciseValidator.description("d".repeat(201)));
    }

    @Test
    @DisplayName("EXR-06 a name that already exists (any case) is a conflict")
    void duplicateName() {
        ExerciseRepository repo = mock(ExerciseRepository.class);
        when(repo.findVisible(7)).thenReturn(LIBRARY);
        ExerciseService service = new ExerciseService(repo);

        breaksRule("EXR-06", HttpStatus.CONFLICT, () -> service.create(7, new ExerciseRequest("  squats ", "QUADS", "")));
        breaksRule("EXR-05", () -> service.create(7, new ExerciseRequest("Hip Thrust", "", "")));
        verify(repo, never()).insert(anyString(), any(), anyString(), anyLong());

        when(repo.insert("Bulgarian Split Squat", QUADS, "", 7)).thenReturn(50L);
        Exercise created = new Exercise(50, "Bulgarian Split Squat", QUADS, "", 7L);
        when(repo.findVisibleById(7, 50)).thenReturn(Optional.of(created));
        assertThat(service.create(7, new ExerciseRequest("Bulgarian Split Squat", "quads", null))).isEqualTo(created);
        assertThat(created.isCustom()).isTrue();
    }

    @Test
    @DisplayName("requireVisible: missing id -> 400, unknown or someone else's -> 404")
    void requireVisible() {
        ExerciseRepository repo = mock(ExerciseRepository.class);
        when(repo.findVisibleById(7, 999)).thenReturn(Optional.empty());
        ExerciseService service = new ExerciseService(repo);
        breaksRule("EXR-01", () -> service.requireVisible(7, null));
        breaksRule("NOT-FOUND", () -> service.requireVisible(7, 999L));
    }
}
