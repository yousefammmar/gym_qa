# Gym Tracker: Software Requirements

System Under Test (SUT) for the Software Quality Assurance project.
Version 1.0 · 2026-11-19 · runs only on `http://localhost:8080`.

Every business rule has an ID. The backend returns that ID as `code` in every error response, for example:

```json
HTTP 400  {"code": "WKT-01", "message": "Repetitions must be a whole number from 1 to 100"}
```

So a tester can trace any HTTP response back to the requirement (and to the tests in [traceability-matrix.md](traceability-matrix.md)).

The numeric limits below are the **proposed application specifications**. The same numbers are used in the Java code (`backend/src/main/java/com/gymtracker/service/*Validator.java`), the database CHECK constraints (`backend/src/main/resources/db/schema.sql`), the tests and this document.

## 1. Specification values at a glance

| Value | Valid range | Rule | Where in code |
| --- | --- | --- | --- |
| Repetitions per set | 1 – 100 (whole number) | WKT-01 | `WorkoutValidator.REPS_MIN/MAX` |
| Weight per set | 0 – 500 kg (decimals allowed, 0 = body weight) | WKT-02 | `WorkoutValidator.WEIGHT_MIN/MAX` |
| Sets per exercise (target and logged) | 1 – 20 | WKT-03 | `WorkoutValidator.SETS_MIN/MAX` |
| Workout duration | 1 – 300 minutes | WKT-04 | `WorkoutValidator.DURATION_MIN/MAX` |
| Username | 3 – 30 chars, `A-Z a-z 0-9 _` | AUTH-01 | `UserValidator` |
| Email | `name@domain.tld`, max 100 chars | AUTH-02 | `UserValidator` |
| Password | 8 – 64 chars, ≥ 1 letter and ≥ 1 digit | AUTH-03 | `UserValidator` |
| Display name | 0 – 50 chars (empty → username) | AUTH-09 | `UserValidator` |
| Login token lifetime | 7 days | AUTH-08 | `AuthService.TOKEN_LIFETIME` |
| Workout name / notes | ≤ 60 / ≤ 500 chars | WKT-08 | `WorkoutValidator` |
| Exercise name / description | 2 – 50 / ≤ 200 chars | EXR-04 / EXR-07 | `ExerciseValidator` |
| Goal title | 1 – 80 chars | GOL-01 | `GoalValidator` |
| Goal target | > 0; weight goal ≤ 500 kg; count goal whole number | GOL-02 / GOL-05 | `GoalValidator` |
| Goal target date | ≥ today when created or changed | GOL-03 | `GoalValidator` |
| Weekly plan | 5 training days (Mon Push, Tue Pull, Wed Legs, Fri Upper, Sat Lower) | PRG-04 | `backend/src/main/resources/db/schema.sql` |

## 2. Functional requirements

### 2.1 User management (AUTH)

| ID | Requirement | Error / result |
| --- | --- | --- |
| AUTH-01 | A username is 3–30 characters of letters, digits or `_`. Leading/trailing spaces are removed. | 400 AUTH-01 |
| AUTH-02 | An email must look like `name@domain.tld` and be at most 100 characters. It is stored in lower case. | 400 AUTH-02 |
| AUTH-03 | A password is 8–64 characters and contains at least one letter **and** at least one digit. | 400 AUTH-03 |
| AUTH-04 | Usernames and emails are unique, ignoring upper/lower case (register and profile edit). | 409 AUTH-04 |
| AUTH-05 | Passwords are stored only as BCrypt hashes. No API response contains a password or hash. | – |
| AUTH-06 | Login needs username and password (400 if blank). Unknown user and wrong password give the same message. | 401 AUTH-06 |
| AUTH-07 | Logout deletes the login token on the server; the token stops working immediately. | 204 |
| AUTH-08 | Every `/api` endpoint except register, login, `/api/plan` and `/api/exercises/muscle-groups` needs `Authorization: Bearer <token>`. Missing, unknown or expired (≥ 7 days old) tokens are refused. | 401 AUTH-08 |
| AUTH-09 | Profile: the user can change display name (≤ 50, empty → username) and email (AUTH-02, AUTH-04). | 400 AUTH-09 |
| AUTH-10 | Changing the password needs the correct current password; the new one follows AUTH-03. Other sessions of the user are signed out. | 400 AUTH-10 |
| AUTH-11 | A user only ever sees and changes their own workouts, goals and custom exercises. Another user's record behaves as if it did not exist. | 404 NOT-FOUND |

### 2.2 Workout management (WKT)

The original Push / Pull / Legs + Upper / Lower program (5 days, 37 exercise entries, rest on Thursday and Sunday) is stored as plan data and shown on the start page and the Plan page.

| ID | Requirement | Error / result |
| --- | --- | --- |
| WKT-01 | Logged repetitions are whole numbers from 1 to 100. A decimal such as 5.5 is refused as a wrong type. | 400 WKT-01 (REQ-01 for 5.5) |
| WKT-02 | Logged weight is 0 to 500 kg. | 400 WKT-02 |
| WKT-03 | Target sets of an exercise in a workout are 1–20, and at most 20 sets can be logged for one exercise in one workout. | 400 WKT-03 |
| WKT-04 | A workout's duration is 1–300 minutes (on completion and when edited). | 400 WKT-04 |
| WKT-05 | A workout cannot be completed unless it contains at least one **valid logged set**: a set that is ticked as completed AND has valid reps (WKT-01) AND a valid weight (WKT-02). | 400 WKT-05 |
| WKT-06 | A completed workout cannot be completed again. | 400 WKT-06 |
| WKT-07 | The completion duration is the minutes the user enters; if none is entered, the minutes since the workout was started (rounded up, at least 1); if the workout was never started, the user must enter it. | 400 WKT-07 |
| WKT-08 | Creating a workout: optional plan day (`push`, `pull`, `legs`, `upper`, `lower`) whose exercises and target sets/reps are copied in; name ≤ 60 (default: plan day name or "Custom workout"); date defaults to today; notes ≤ 500. Unknown plan day is refused. | 201 / 400 WKT-08 |
| WKT-09 | A workout dated in the future can be planned, but not completed, and a completed workout cannot be moved to a future date. | 400 WKT-09 |
| WKT-10 | Life cycle PLANNED → IN_PROGRESS → COMPLETED. Only a PLANNED workout can be started. Logging the first set of a PLANNED workout starts it automatically. | 400 WKT-10 |
| WKT-11 | A workout can be edited (name, date, notes, duration, sets, exercises) and deleted. Deleting removes its exercises and sets. The website asks for confirmation first. | 204 |
| WKT-12 | An edit (un-tick, delete set, remove exercise) may not leave a COMPLETED workout without a valid logged set. | 400 WKT-12 |

### 2.3 Exercise management (EXR)

| ID | Requirement | Error / result |
| --- | --- | --- |
| EXR-01 | The library shows the 27 built-in exercises plus the user's own custom exercises, sorted by name. | 200 |
| EXR-02 | Search: case-insensitive text match on name **or** description, surrounding spaces ignored; empty search = everything. | 200 |
| EXR-03 | Filter by muscle group (CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, QUADS, HAMSTRINGS, GLUTES, CALVES, CORE, FULL_BODY), case-insensitive, combinable with search (both must match). Unknown group is refused. | 400 EXR-03 |
| EXR-04 | Custom exercise name: 2–50 characters. | 400 EXR-04 |
| EXR-05 | Custom exercise muscle group is required and must be one of the list. | 400 EXR-05 |
| EXR-06 | A custom exercise name may not repeat (ignoring case) a name the user already sees. | 409 EXR-06 |
| EXR-07 | Custom exercise description is optional, ≤ 200 characters. | 400 EXR-07 |

### 2.4 Progress tracking (PRG)

| ID | Requirement |
| --- | --- |
| PRG-01 | Total completed workouts = number of the user's workouts with status COMPLETED. |
| PRG-02 | Training volume = Σ (weight × repetitions) over all **completed sets** of **completed workouts**. |
| PRG-03 | Current streak = consecutive calendar days with ≥ 1 completed workout, ending today, or yesterday if nothing is done today yet. Longest streak = longest such run ever. Several workouts on one day count as one day. |
| PRG-04 | Weekly progress (weeks Monday–Sunday): completed workouts and volume for this week and the 7 weeks before it; this week's percent = completed ÷ 5 planned training days, rounded down, at most 100 %. |
| PRG-05 | Exercise progression: for one exercise, per completed workout day: heaviest completed set, volume, number of completed sets, in date order. |
| PRG-06 | History: the user's workouts newest first, filterable by status (`?status=COMPLETED` is the training history). |
| PRG-07 | PLANNED and IN_PROGRESS workouts never count in PRG-01 … PRG-05. All charts on the website are drawn from these API values (real database records). |

### 2.5 Goal management (GOL)

| ID | Requirement | Error / result |
| --- | --- | --- |
| GOL-01 | Title 1–80 characters. | 400 GOL-01 |
| GOL-02 | Target value must be positive (> 0). | 400 GOL-02 |
| GOL-03 | Target date is required and cannot be in the past when a goal is created. On edit, an unchanged date may stay, a changed date must be today or later. | 400 GOL-03 |
| GOL-04 | Type is WORKOUT_COUNT, TOTAL_VOLUME or EXERCISE_MAX_WEIGHT; EXERCISE_MAX_WEIGHT needs an exercise the user can see. | 400 GOL-04 |
| GOL-05 | EXERCISE_MAX_WEIGHT target ≤ 500 kg; WORKOUT_COUNT target is a whole number. | 400 GOL-05 |
| GOL-06 | Progress is calculated from stored data each time, counting completed work dated on/after the goal's start date (creation day): count of completed workouts, volume (PRG-02 rule), or heaviest completed set of the exercise. |
| GOL-07 | Percent = ⌊current ÷ target × 100⌋, capped at 100. |
| GOL-08 | "Mark completed" works only for an ACTIVE goal at 100 %. | 400 GOL-08 |
| GOL-09 | A completed goal can no longer be edited (it can be deleted). | 400 GOL-09 |
| GOL-10 | Displayed state: COMPLETED if marked completed; else ACHIEVED if 100 %; else OVERDUE if the target date has passed; else ACTIVE. |

### 2.6 General request rules (REQ)

| ID | Requirement | Error |
| --- | --- | --- |
| REQ-00 | Framework-level errors keep their HTTP status (405, 415, …). | as status |
| REQ-01 | Malformed JSON or a value of the wrong type. | 400 |
| REQ-02 | Invalid path or query parameter (e.g. `/api/workouts/abc`, `?status=DONE`). | 400 |
| REQ-03 | Logging a set without saying which exercise it belongs to. | 400 |

## 3. Non-functional requirements

| ID | Requirement |
| --- | --- |
| NFR-01 | Runs entirely on localhost with `./mvnw spring-boot:run` (or `mvn spring-boot:run`) from `backend/`; one Spring Boot process serves the website (`frontend/`) and the REST API on port 8080. No hosting, cloud, Docker or CI/CD. |
| NFR-02 | Works offline after Maven has downloaded the dependencies (no CDN, fonts or chart libraries are loaded). |
| NFR-03 | Data persists in the SQLite file `backend/data/gym-tracker.db`, created automatically and ignored by Git. |
| NFR-04 | Frontend: HTML, CSS and vanilla JavaScript only. Backend: Java 17, Spring Boot. |
| NFR-05 | Responsive from 375 px phones to desktop, no horizontal scrolling; keyboard-usable forms with labels; charts have a text alternative. |
| NFR-06 | Security basics: BCrypt hashes, random 256-bit tokens with 7-day expiry, user data isolation (AUTH-11), user text escaped before it is shown (no HTML injection). |
| NFR-07 | Testability: business rules live in Java classes in the `service` package (services and validators), many as pure static methods; a `Clock` bean controls "now". |

## 4. API overview

| Method & path | Purpose | Rules |
| --- | --- | --- |
| `POST /api/auth/register` | create account, returns token | AUTH-01…05 |
| `POST /api/auth/login` | returns token | AUTH-06 |
| `POST /api/auth/logout` | delete token | AUTH-07 |
| `GET/PUT /api/profile`, `PUT /api/profile/password` | profile management | AUTH-09, AUTH-10 |
| `GET /api/plan` (public) | the 5-day program | WKT-08 |
| `GET /api/exercises?search=&muscleGroup=`, `POST /api/exercises` | library, custom exercises | EXR-01…07 |
| `GET /api/exercises/muscle-groups` (public) | filter values | EXR-03 |
| `GET /api/workouts?status=`, `POST /api/workouts` | history, create | WKT-08, PRG-06 |
| `GET/PUT/DELETE /api/workouts/{id}` | read, edit, delete | WKT-04, 09, 11 |
| `POST /api/workouts/{id}/start`, `/complete` | life cycle | WKT-04…07, 09, 10 |
| `POST /api/workouts/{id}/exercises`, `DELETE …/exercises/{blockId}` | add/remove exercise | WKT-03, 12 |
| `POST /api/workouts/{id}/sets`, `PUT/DELETE …/sets/{setId}` | log/edit/delete sets | WKT-01…03, 12 |
| `GET /api/progress`, `GET /api/progress/exercises/{id}` | statistics | PRG-01…07 |
| `GET /api/dashboard` | home page data | PRG, GOL |
| `GET/POST /api/goals`, `PUT/DELETE /api/goals/{id}`, `POST /api/goals/{id}/complete` | goals | GOL-01…10 |
