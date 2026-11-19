-- =====================================================================
-- Gym Tracker database (SQLite). Runs on every application start.
-- Safe to re-run: tables use CREATE TABLE IF NOT EXISTS and the seed
-- rows use INSERT OR IGNORE with fixed ids, so user data is never lost.
--
-- Seed data = the original website's plan (script.js, commit 2a2fc44):
-- 27 distinct exercises, 5 training days, 37 plan entries.
-- Dates are stored as TEXT 'yyyy-MM-dd', timestamps as ISO-8601 TEXT.
-- =====================================================================

CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT NOT NULL UNIQUE COLLATE NOCASE,
    email         TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT NOT NULL,
    display_name  TEXT NOT NULL,
    created_at    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS auth_tokens (
    token      TEXT PRIMARY KEY,
    user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TEXT NOT NULL
);

-- owner_id NULL = built-in exercise, otherwise a user's custom exercise
CREATE TABLE IF NOT EXISTS exercises (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT NOT NULL,
    muscle_group TEXT NOT NULL,
    description  TEXT NOT NULL DEFAULT '',
    owner_id     INTEGER REFERENCES users(id) ON DELETE CASCADE
);

-- The weekly program: weekday 1 = Monday ... 7 = Sunday. Days without a row are rest days.
CREATE TABLE IF NOT EXISTS plan_days (
    slug         TEXT PRIMARY KEY,
    name         TEXT NOT NULL,
    focus        TEXT NOT NULL,
    prescription TEXT NOT NULL,
    weekday      INTEGER NOT NULL UNIQUE,
    position     INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS plan_exercises (
    id          INTEGER PRIMARY KEY,
    plan_slug   TEXT NOT NULL REFERENCES plan_days(slug),
    position    INTEGER NOT NULL,
    exercise_id INTEGER NOT NULL REFERENCES exercises(id),
    target_sets INTEGER NOT NULL,
    target_reps TEXT NOT NULL,
    note        TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS workouts (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id          INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_slug        TEXT REFERENCES plan_days(slug),
    name             TEXT NOT NULL,
    workout_date     TEXT NOT NULL,
    status           TEXT NOT NULL CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED')),
    started_at       TEXT,
    completed_at     TEXT,
    duration_minutes INTEGER CHECK (duration_minutes IS NULL OR duration_minutes BETWEEN 1 AND 300),
    notes            TEXT NOT NULL DEFAULT '',
    created_at       TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS workout_exercises (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    workout_id  INTEGER NOT NULL REFERENCES workouts(id) ON DELETE CASCADE,
    exercise_id INTEGER NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    target_sets INTEGER NOT NULL CHECK (target_sets BETWEEN 1 AND 20),
    target_reps TEXT NOT NULL DEFAULT ''
);

-- The CHECKs repeat the Java rules (WorkoutValidator) as a last line of defence.
CREATE TABLE IF NOT EXISTS workout_sets (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    workout_exercise_id INTEGER NOT NULL REFERENCES workout_exercises(id) ON DELETE CASCADE,
    reps                INTEGER NOT NULL CHECK (reps BETWEEN 1 AND 100),
    weight_kg           REAL NOT NULL CHECK (weight_kg BETWEEN 0 AND 500),
    completed           INTEGER NOT NULL DEFAULT 1,
    created_at          TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS goals (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title        TEXT NOT NULL,
    goal_type    TEXT NOT NULL CHECK (goal_type IN ('WORKOUT_COUNT', 'TOTAL_VOLUME', 'EXERCISE_MAX_WEIGHT')),
    exercise_id  INTEGER REFERENCES exercises(id) ON DELETE CASCADE,
    target_value REAL NOT NULL CHECK (target_value > 0),
    start_date   TEXT NOT NULL,
    target_date  TEXT NOT NULL,
    status       TEXT NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED')),
    created_at   TEXT NOT NULL,
    completed_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_workouts_user ON workouts(user_id, workout_date);
CREATE INDEX IF NOT EXISTS idx_workout_exercises_workout ON workout_exercises(workout_id);
CREATE INDEX IF NOT EXISTS idx_sets_workout_exercise ON workout_sets(workout_exercise_id);
CREATE INDEX IF NOT EXISTS idx_goals_user ON goals(user_id);

-- ---------------------------------------------------------------------
-- Seed: built-in exercise library (description = the original "type" label)
-- ---------------------------------------------------------------------
INSERT OR IGNORE INTO exercises (id, name, muscle_group, description) VALUES
 (1,  'Dumbbell Press',               'CHEST',      'Chest press'),
 (2,  'Incline Smith Press',          'CHEST',      'Upper chest'),
 (3,  'Chest Flies',                  'CHEST',      'Chest isolation'),
 (4,  'Smith Machine Shoulder Press', 'SHOULDERS',  'Shoulders'),
 (5,  'Cable Flies',                  'CHEST',      'Chest isolation'),
 (6,  'Triceps Pushdown',             'TRICEPS',    'Triceps'),
 (7,  'Overhead Extension',           'TRICEPS',    'Triceps long head'),
 (8,  'Lat Pulldown',                 'BACK',       'Vertical pull'),
 (9,  'Wide Grip Row',                'BACK',       'Row'),
 (10, 'Single Arm Row',               'BACK',       'Unilateral row'),
 (11, 'High Row',                     'BACK',       'Upper back'),
 (12, 'Single Arm Preacher Curl',     'BICEPS',     'Biceps'),
 (13, 'Reverse Grip Biceps Curl',     'BICEPS',     'Biceps / forearms'),
 (14, 'Spider Curl',                  'BICEPS',     'Biceps'),
 (15, 'Squats',                       'QUADS',      'Compound'),
 (16, 'Romanian Deadlift',            'HAMSTRINGS', 'Hamstrings'),
 (17, 'Leg Extension',                'QUADS',      'Quads'),
 (18, 'Leg Curls',                    'HAMSTRINGS', 'Hamstrings'),
 (19, 'Standing Calf Raise',          'CALVES',     'Calves'),
 (20, 'High-Low Plank',               'CORE',       'Core'),
 (21, 'Hanging Leg Raises',           'CORE',       'Core'),
 (22, 'Incline Dumbbell Press',       'CHEST',      'Chest press'),
 (23, 'Dumbbell Shoulder Press',      'SHOULDERS',  'Shoulders'),
 (24, 'Dumbbell Shoulder Fly',        'SHOULDERS',  'Side delts'),
 (25, 'Cable Biceps Curl',            'BICEPS',     'Biceps'),
 (26, 'Kneeling Twists',              'CORE',       'Core'),
 (27, 'Back Extension',               'BACK',       'Lower back');

-- ---------------------------------------------------------------------
-- Seed: the 5-day Push / Pull / Legs + Upper / Lower program
-- Mon Push, Tue Pull, Wed Legs, Thu rest, Fri Upper, Sat Lower, Sun rest
-- ---------------------------------------------------------------------
INSERT OR IGNORE INTO plan_days (slug, name, focus, prescription, weekday, position) VALUES
 ('push',  'Push Day',       'Chest • Shoulders • Triceps',        '3 × 6–10 reps', 1, 1),
 ('pull',  'Pull Day',       'Back • Biceps',                      '3 × 6–10 reps', 2, 2),
 ('legs',  'Leg Day + Core', 'Quads • Hamstrings • Calves • Core', 'Mixed',         3, 3),
 ('upper', 'Upper',          'Chest • Back • Shoulders • Arms',    'Mixed',         5, 4),
 ('lower', 'Lower',          'Lower Body • Posterior Chain',       'Mixed',         6, 5);

INSERT OR IGNORE INTO plan_exercises (id, plan_slug, position, exercise_id, target_sets, target_reps, note) VALUES
 (1,  'push', 1, 1,  3, '6–10', 'Chest press'),
 (2,  'push', 2, 2,  3, '6–10', 'Upper chest'),
 (3,  'push', 3, 3,  3, '6–10', 'Chest isolation'),
 (4,  'push', 4, 4,  3, '6–10', 'Shoulders'),
 (5,  'push', 5, 5,  3, '6–10', 'Chest isolation'),
 (6,  'push', 6, 6,  3, '6–10', 'Triceps'),
 (7,  'push', 7, 7,  3, '6–10', 'Triceps long head'),
 (8,  'pull', 1, 8,  3, '6–10', 'Vertical pull'),
 (9,  'pull', 2, 9,  3, '6–10', 'Row'),
 (10, 'pull', 3, 10, 3, '6–10', 'Unilateral row'),
 (11, 'pull', 4, 11, 3, '6–10', 'Upper back'),
 (12, 'pull', 5, 12, 3, '6–10', 'Biceps'),
 (13, 'pull', 6, 13, 3, '6–10', 'Biceps / forearms'),
 (14, 'pull', 7, 14, 3, '6–10', 'Biceps'),
 (15, 'legs', 1, 15, 3, '6–10', 'Compound'),
 (16, 'legs', 2, 16, 3, '6–10', 'Hamstrings'),
 (17, 'legs', 3, 17, 3, '6–10', 'Quads'),
 (18, 'legs', 4, 18, 3, '6–10', 'Hamstrings'),
 (19, 'legs', 5, 19, 3, '6–10', 'Calves'),
 (20, 'legs', 6, 20, 2, 'to form-failure', 'Core'),
 (21, 'legs', 7, 21, 2, 'to form-failure', 'Core'),
 (22, 'upper', 1, 22, 3, '6–10', 'Chest press'),
 (23, 'upper', 2, 3,  3, '6–10', 'Chest isolation'),
 (24, 'upper', 3, 8,  3, '6–10', 'Back'),
 (25, 'upper', 4, 9,  3, '6–10', 'Back'),
 (26, 'upper', 5, 23, 3, '6–10', 'Shoulders'),
 (27, 'upper', 6, 24, 3, '6–10', 'Side delts'),
 (28, 'upper', 7, 6,  3, '6–10', 'Triceps'),
 (29, 'upper', 8, 25, 3, '6–10', 'Biceps'),
 (30, 'upper', 9, 26, 2, '15',   'Core'),
 (31, 'lower', 1, 15, 3, '6–10', 'Compound'),
 (32, 'lower', 2, 16, 3, '6–10', 'Hamstrings'),
 (33, 'lower', 3, 17, 3, '6–10', 'Quads'),
 (34, 'lower', 4, 18, 3, '6–10', 'Hamstrings'),
 (35, 'lower', 5, 19, 3, '6–10', 'Calves'),
 (36, 'lower', 6, 27, 3, '6–10', 'Lower back'),
 (37, 'lower', 7, 27, 2, 'to form-failure', 'Lower back finisher');
