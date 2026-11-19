# Gym Tracker: Requirements Traceability Matrix

Requirement → code that implements it → tests that verify it → test design technique.
Test names are `Class.method` (unit tests in `backend/src/test/java/com/gymtracker/unit`, integration tests in `.../integration`).
Techniques: EP equivalence partitioning · BVA boundary values · CE cause–effect · DT decision table · MCC multiple condition coverage · BP basis path · DF data flow · MUT mutation-driven · INT integration · E2E system scenario.

Status was taken from the 2026-11-19 run (244/244 passed). Re-check it after changes with `./mvnw test` in `backend/`.

## User management

| Req | Implemented in | Unit tests | Integration / system tests | Techniques | Status |
| --- | --- | --- | --- | --- | --- |
| AUTH-01 | `UserValidator.username` | `UserValidatorTest.Username.*` | `AuthIntegrationTest.registerValidation` | EP, BVA | Pass |
| AUTH-02 | `UserValidator.email` | `UserValidatorTest.Email.*` | `AuthIntegrationTest.registerValidation` | EP, BVA | Pass |
| AUTH-03 | `UserValidator.password` | `UserValidatorTest.Password.boundaryLengths`, `.letterAndDigitCombinations`, `.symbolIsNotADigit`, `.spacesAndSymbolsAreAllowed`; `AuthServiceTest.registerValidatesFirst` | `AuthIntegrationTest.registerValidation` | EP, BVA, CE-4, MCC, BP, MUT | Pass |
| AUTH-04 | `AuthService.register`, `.updateProfile` | `AuthServiceTest.registerRejectsDuplicateUsername`, `.registerRejectsDuplicateEmail` | `AuthIntegrationTest.duplicates`, `.profile` | EP | Pass |
| AUTH-05 | `AuthService.register` + `BCryptPasswordEncoder` | `AuthServiceTest.registerHashesPassword` | `AuthIntegrationTest.registerStoresHash` | INT | Pass |
| AUTH-06 | `AuthService.login` | `AuthServiceTest.loginBlankFields`, `.loginFailures`, `.loginSuccess`, `.tokensAreRandom` | `AuthIntegrationTest.login`, `EndToEndScenarioTest.fullJourney` | DT-4, MUT | Pass |
| AUTH-07 | `AuthService.logout` | – | `AuthIntegrationTest.logoutAndExpiry`, `EndToEndScenarioTest.fullJourney` | INT, E2E | Pass |
| AUTH-08 | `AuthInterceptor`, `WebConfig`, `AuthService.userIdForToken`, `.isExpired` | `AuthServiceTest.tokenHeaderFormat`, `.tokenLifetime`, `.expiryBoundary` | `AuthIntegrationTest.protectedEndpoints`, `.logoutAndExpiry` | BVA, INT | Pass |
| AUTH-09 | `UserValidator.displayName`, `AuthService.updateProfile` | `UserValidatorTest.DisplayName_.*` | `AuthIntegrationTest.profile` | BVA | Pass |
| AUTH-10 | `AuthService.changePassword` | `AuthServiceTest.changePassword` | `AuthIntegrationTest.profile` | EP | Pass |
| AUTH-11 | every `require…(userId, id)` / repository query scoped by `user_id` | `WorkoutServiceTest.otherUsersWorkout`, `ExerciseServiceTest.requireVisible` | `WorkoutIntegrationTest.otherUserIsolation`, `ExerciseIntegrationTest.customExercises`, `GoalIntegrationTest.weightGoalAndIsolation` | INT | Pass |

## Workout management

| Req | Implemented in | Unit tests | Integration / system tests | Techniques | Status |
| --- | --- | --- | --- | --- | --- |
| WKT-01 | `WorkoutValidator.isValidReps/requireReps`; DB CHECK | `WorkoutValidatorTest.repetitionBoundaries`; `WorkoutServiceTest.logSetValidation`, `.updateSetPartial` | `WorkoutIntegrationTest.setBoundaries`, `DatabaseIntegrationTest.checkConstraints` | EP, BVA | Pass |
| WKT-02 | `WorkoutValidator.isValidWeight/requireWeight`; DB CHECK | `WorkoutValidatorTest.weightBoundaries`, `.weightNaN`; `WorkoutServiceTest.logSetValidation`, `.updateSetPartial` | `WorkoutIntegrationTest.setBoundaries`, `DatabaseIntegrationTest.checkConstraints` | EP, BVA | Pass |
| WKT-03 | `WorkoutValidator.requireSetCount`, `.targetReps`; `WorkoutService.logSet`, `.addExercise` | `WorkoutValidatorTest.setCountBoundaries`, `.textLimits`; `WorkoutServiceTest.setCountLimit`, `.setsPerBlock` | `WorkoutIntegrationTest.setCountLimit` | EP, BVA | Pass |
| WKT-04 | `WorkoutValidator.requireDuration`; `WorkoutService.completionMinutes`, `.update` | `WorkoutValidatorTest.durationBoundaries`; `WorkoutCompletionTest.r4r5RequestedMinutes`, `.r7MeasuredTooLong`; `WorkoutServiceTest.updateWorkoutRules` | `WorkoutIntegrationTest.completion` | BVA, DT-1 | Pass |
| WKT-05 | `WorkoutService.completionMinutes`, `.countValidSets` | `WorkoutCompletionTest.r3NoValidSet`, `.validSetConditions`, `.countValidSetsLoop` | `WorkoutIntegrationTest.completion` | CE-1, CE-2, DT-1, MCC, BP | Pass |
| WKT-06 | `WorkoutService.completionMinutes` | `WorkoutCompletionTest.r1AlreadyCompleted`, `.ruleOrder` | `WorkoutIntegrationTest.completion` | DT-1, BP | Pass |
| WKT-07 | `WorkoutService.completionMinutes`, `.minutesSince` | `WorkoutCompletionTest.r6MeasuredFromStart`, `.r8NeverStarted`, `.minutesSince`; `WorkoutServiceTest.completeStoresDuration` | `WorkoutIntegrationTest.completionMeasured`, `.startRules` | DT-1, BVA, BP | Pass |
| WKT-08 | `WorkoutService.create`; `WorkoutValidator.name`, `.notes` | `WorkoutServiceTest.createFromPlan`, `.createUnknownPlan`, `.createCustom`, `.createWithNothing`; `WorkoutValidatorTest.nameRules`, `.textLimits` | `WorkoutIntegrationTest.createFromPlan` | EP, BVA | Pass |
| WKT-09 | `WorkoutService.completionMinutes`, `.update` | `WorkoutCompletionTest.r2FutureDate`; `WorkoutServiceTest.updateWorkoutRules` | `WorkoutIntegrationTest.futureDate`, `.editRecords` | DT-1, BVA | Pass |
| WKT-10 | `WorkoutService.start`, `.logSet` (auto-start) | `WorkoutServiceTest.startRules`, `.logSetAutoStart` | `WorkoutIntegrationTest.startRules`, `.setBoundaries` | EP | Pass |
| WKT-11 | `WorkoutService.update`, `.delete`, `.updateSet`, `.addExercise`; ON DELETE CASCADE; `confirm()` in `frontend/js/workout.js` | `WorkoutServiceTest.updateSetPartial`, `.updateWorkoutRules`, `.updateKeepsNotes` | `WorkoutIntegrationTest.editRecords`, `.deleteCascades` | INT | Pass |
| WKT-12 | `WorkoutService.ensureCompletedKeepsValidSet` | `WorkoutCompletionTest.completedKeepsValidSet`; `WorkoutServiceTest.completedWorkoutKeepsASet`, `.completedWorkoutWithTwoBlocks` | `WorkoutIntegrationTest.editRecords` | DT, MUT | Pass |

## Exercise management

| Req | Implemented in | Unit tests | Integration / system tests | Techniques | Status |
| --- | --- | --- | --- | --- | --- |
| EXR-01 | `ExerciseRepository.findVisible`, `ExerciseService.list`, `.requireVisible` | `ExerciseServiceTest.requireVisible` | `ExerciseIntegrationTest.searchAndFilter`, `DatabaseIntegrationTest.seedMatchesOriginalWebsite` | INT | Pass |
| EXR-02 | `ExerciseService.filter` | `ExerciseServiceTest.filterCombinations`, `.filterResults` | `ExerciseIntegrationTest.searchAndFilter` | EP, MCC | Pass |
| EXR-03 | `ExerciseService.filter`, `ExerciseValidator.muscleGroup` | `ExerciseServiceTest.filterCombinations`, `.muscleGroup` | `ExerciseIntegrationTest.searchAndFilter` | EP | Pass |
| EXR-04 | `ExerciseValidator.name` | `ExerciseServiceTest.nameBoundaries` | `ExerciseIntegrationTest.customExercises` | BVA | Pass |
| EXR-05 | `ExerciseValidator.muscleGroup(…, true)` | `ExerciseServiceTest.muscleGroup`, `.duplicateName` | `ExerciseIntegrationTest.customExercises` | EP, MUT | Pass |
| EXR-06 | `ExerciseService.create` | `ExerciseServiceTest.duplicateName` | `ExerciseIntegrationTest.customExercises` | EP | Pass |
| EXR-07 | `ExerciseValidator.description` | `ExerciseServiceTest.description` | – | BVA | Pass |

## Progress tracking

| Req | Implemented in | Unit tests | Integration / system tests | Techniques | Status |
| --- | --- | --- | --- | --- | --- |
| PRG-01 | `ProgressService.countCompleted` | `ProgressServiceTest.countCompleted` | `ProgressIntegrationTest.completedOnly`, `EndToEndScenarioTest.fullJourney` | EP | Pass |
| PRG-02 | `ProgressService.trainingVolume` | `ProgressServiceTest.Volume.*` | `ProgressIntegrationTest.completedOnly`, `EndToEndScenarioTest.fullJourney` | EP, MCC | Pass |
| PRG-03 | `ProgressService.currentStreak`, `.longestStreak` | `ProgressServiceTest.Streaks.*` | `ProgressIntegrationTest.completedOnly` | BP, LCSAJ, DF, BVA | Pass |
| PRG-04 | `ProgressService.weekStart`, `.weekIndex`, `.weekPercent`, `.weeklyStats`, `.countCompletedBetween` | `ProgressServiceTest.Weekly.*` | `ProgressIntegrationTest.emptyProgress`, `.completedOnly`, `EndToEndScenarioTest.fullJourney` | BVA, EP | Pass |
| PRG-05 | `ProgressService.exerciseProgression` | `ProgressServiceTest.exerciseProgression` | `ProgressIntegrationTest.exerciseProgression` | EP | Pass |
| PRG-06 | `WorkoutService.list`, `WorkoutRepository.findWorkouts` | – | `ProgressIntegrationTest.completedOnly`, `WorkoutIntegrationTest.requestErrors` | INT | Pass |
| PRG-07 | status checks in every `ProgressService` / `GoalService` calculation | `ProgressServiceTest.*` (IN_PROGRESS / PLANNED rows in every data set) | `ProgressIntegrationTest.completedOnly`, `EndToEndScenarioTest.fullJourney` | EP | Pass |

## Goal management

| Req | Implemented in | Unit tests | Integration / system tests | Techniques | Status |
| --- | --- | --- | --- | --- | --- |
| GOL-01 | `GoalValidator.title` | `GoalRulesTest.Validation.title` | – | BVA | Pass |
| GOL-02 | `GoalValidator.targetValue` | `GoalRulesTest.Validation.targetValue` | `GoalIntegrationTest.validation` | EP, BVA | Pass |
| GOL-03 | `GoalValidator.targetDate` | `GoalRulesTest.Validation.targetDateOnCreate`, `.targetDateOnUpdate` | `GoalIntegrationTest.validation` | CE-3, MCC, BVA, MUT | Pass |
| GOL-04 | `GoalValidator.type`, `GoalService.create` | `GoalRulesTest.Validation.type`, `.WithMocks.weightGoalNeedsExercise`, `.createAndEdit` | `GoalIntegrationTest.validation` | EP | Pass |
| GOL-05 | `GoalValidator.targetValue` | `GoalRulesTest.Validation.targetValue` | – | EP, BVA | Pass |
| GOL-06 | `GoalService.currentValue` | `GoalRulesTest.CurrentValue.*`, `.WithMocks.listShowsProgress` | `GoalIntegrationTest.progressAndCompletion`, `.weightGoalAndIsolation` | EP, MUT | Pass |
| GOL-07 | `GoalService.percent` | `GoalRulesTest.percent` | `GoalIntegrationTest.progressAndCompletion` | BVA | Pass |
| GOL-08 | `GoalService.complete` | `GoalRulesTest.WithMocks.completeTooEarly`, `.completeAtTarget` | `GoalIntegrationTest.progressAndCompletion`, `EndToEndScenarioTest.fullJourney` | DT-3 | Pass |
| GOL-09 | `GoalService.update` | `GoalRulesTest.WithMocks.editCompleted` | `GoalIntegrationTest.progressAndCompletion` | DT | Pass |
| GOL-10 | `GoalService.state` | `GoalRulesTest.state` | `GoalIntegrationTest.progressAndCompletion` | DT-2, BP | Pass |

## General and non-functional

| Req | Implemented in | Tests | Status |
| --- | --- | --- | --- |
| REQ-00 … REQ-03 | `GlobalExceptionHandler`, `WorkoutService.logSet` | `WorkoutIntegrationTest.requestErrors`, `.setBoundaries`; `AuthIntegrationTest.registerValidation`; `WorkoutServiceTest.logSetValidation` | Pass |
| NFR-01 localhost, single process | `GymTrackerApplication`, `frontend/` served via `spring.web.resources.static-locations` | `EndToEndScenarioTest.staticSite`; manual run (README) | Pass |
| NFR-03 SQLite persistence | `application.properties`, `db/schema.sql` | `DatabaseIntegrationTest.*`, `EndToEndScenarioTest.fullJourney` (step 9) | Pass |
| NFR-05 responsive / accessible | `frontend/css/style.css`, the 10 HTML pages | manual checks, test-plan section 8 | Pass (manual) |
| NFR-06 security basics | BCrypt, token expiry, isolation, `escapeHtml` | AUTH-05/08/11 rows above; `AuthServiceTest.tokensAreRandom` | Pass |
