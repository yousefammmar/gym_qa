# Gym Tracker: PPL + Upper/Lower

A gym tracker that runs **only on your own computer**. It grew out of the original static
PPL + Upper/Lower plan website (see [BASELINE.md](BASELINE.md)) and is the System Under Test for a
university Software Quality Assurance project.

## The two halves

```
GYM/
├── frontend/      the website: HTML, CSS, JavaScript (what you see in the browser)
├── backend/       the Java server: rules, database, tests
├── docs/          requirements, test plan, system test plan, traceability matrix
├── README.md  BASELINE.md  WEBSITE_GUIDE.md  BUTTON_GUIDE.md
```

**frontend/**: one HTML file per page and one JS file per page, same name:

```
frontend/
├── index.html      js/home.js        home page + the weekly plan (Start buttons when logged in)
├── login.html      js/login.js       log in
├── register.html   js/register.js    create an account
├── dashboard.html  js/dashboard.js   today, weekly tracker, numbers, recent workouts, goals
├── workouts.html   js/workouts.js    new workout + history
├── workout.html    js/workout.js     one workout: log sets, complete, edit, delete
├── exercises.html  js/exercises.js   exercise library, search, custom exercises
├── progress.html   js/progress.js    totals, streaks, charts, history
├── goals.html      js/goals.js       goals
├── profile.html    js/profile.js     account details and password
├── css/style.css                     the one stylesheet
└── js/common.js, js/charts.js        shared by all pages (talking to the server, charts)
```

**backend/**: a standard Spring Boot project:

```
backend/
├── pom.xml                 build file (also JaCoCo and PIT)
├── mvnw                    Maven wrapper: no Maven install needed
├── data/                   the SQLite database file (created on first start, not in Git)
└── src/
    ├── main/java/com/gymtracker/
    │   ├── controller/     the web addresses (/api/...), one class per area
    │   ├── service/        the rules: AuthService, WorkoutService, ProgressService, GoalService,
    │   │                   ExerciseService and the limits in *Validator (reps, weight, sets, ...)
    │   ├── repository/     the SQL that reads and writes the database
    │   └── model/          plain data classes (User, Workout, Goal, requests and responses)
    ├── main/resources/
    │   ├── application.properties   settings (port, database file, where the frontend is)
    │   └── db/schema.sql            the tables + the original plan as seed data
    └── test/java/com/gymtracker/
        ├── unit/           fast tests of the rules
        └── integration/    whole app + database through HTTP
```

How they connect: the browser loads a page from `frontend/`, and that page's JS calls the backend at
`/api/...`. One Java process serves both, on **http://localhost:8080**. There's no separate frontend server, and no hosting, cloud, Docker or CI/CD.

## 1. What you need

- **JDK 17** or newer (`java -version`).
- Internet **once**, so Maven can download the libraries. After that it works offline.

## 2. Run it

```bash
cd backend
./mvnw spring-boot:run
```

Wait for `Started GymTrackerApplication`, open **http://localhost:8080**, stop with `Ctrl+C`.
(With Maven installed you can type `mvn` instead of `./mvnw`; on Windows use `mvnw.cmd`.)

1. **Start Tracking** → create an account.
2. **Plan** → **Start Push Day** (or any day).
3. Enter weight and reps → **Log set** → **Complete workout**.
4. Look at **Progress**, set **Goals**, add your own **Exercises**.

Your data is in `backend/data/gym-tracker.db`. Delete that file (with the app stopped) to start over.
Frontend edits show up after a browser refresh; Java edits need a restart.

## 3. Tests, coverage, mutation testing (from `backend/`)

```bash
./mvnw test
```

All 244 JUnit tests. Coverage report: `backend/target/site/jacoco/index.html`.
The tests use their own database file in `backend/target/`, never your data.

```bash
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage
```

Mutation testing of the rules. Report: `backend/target/pit-reports/index.html`.

The recorded results of a real run are in [docs/test-plan.md, section 9](docs/test-plan.md#9-execution-record-2026-11-19).

## 4. API

Every error looks like `{"code": "WKT-01", "message": "..."}`; the code is the rule ID from
[docs/requirements.md](docs/requirements.md). All endpoints except register, login, `/api/plan` and
`/api/exercises/muscle-groups` need the header `Authorization: Bearer <token>` (the token comes from login).

| Endpoint | What it does |
| --- | --- |
| `POST /api/auth/register`, `/api/auth/login`, `/api/auth/logout` | account and token |
| `GET/PUT /api/profile`, `PUT /api/profile/password` | profile |
| `GET /api/plan` | the 5-day program |
| `GET /api/exercises?search=&muscleGroup=`, `POST /api/exercises` | library, custom exercises |
| `GET/POST /api/workouts`, `GET/PUT/DELETE /api/workouts/{id}` | workouts |
| `POST /api/workouts/{id}/start`, `/complete` | start / finish |
| `POST /api/workouts/{id}/exercises`, `DELETE …/exercises/{blockId}` | exercises in a workout |
| `POST /api/workouts/{id}/sets`, `PUT/DELETE …/sets/{setId}` | sets |
| `GET /api/progress`, `/api/progress/exercises/{id}`, `/api/dashboard` | statistics |
| `GET/POST /api/goals`, `PUT/DELETE /api/goals/{id}`, `POST /api/goals/{id}/complete` | goals |

## 5. Troubleshooting

| Problem | Fix |
| --- | --- |
| `no such file or directory: ./mvnw` | you're not in `backend/`: run `cd backend` first |
| `release version 17 not supported` | your Java is older than 17; install or select JDK 17+ (`JAVA_HOME`) |
| Port 8080 already in use | another copy is running; stop it with `Ctrl+C` in its terminal |
| Page says "Cannot reach the server" | the Java app stopped; start it again |
| "Your session expired" | logins last 7 days; log in again |
