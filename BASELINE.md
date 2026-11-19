# Baseline: the original website

The starting point of this project is the static site in the GitHub repository `yousefammmar/GYM`,
commit `2a2fc44` ("Add files via upload", 2026-06-16). It is still in this repository's Git history:

```bash
git show 2a2fc44 --stat
```

## What the original had

| Part | Original (commit 2a2fc44) |
| --- | --- |
| Files | `index.html` (~130 lines), `styles.css` (~580), `script.js` (~240). No build, no libraries. |
| Plan | 5 training days: Push Day, Pull Day, Leg Day + Core, Upper, Lower; 37 exercise entries (27 distinct exercises) with sets × reps, hard-coded in `script.js`. |
| Weekly flow | Mon Push, Tue Pull, Wed Legs, Thu rest, Fri Upper, Sat Lower, Sun rest. |
| Tracking | A checkbox and a free-text note per exercise, saved in `localStorage` of one browser. |
| Tracker | "X of 37 completed" + percent bar; "Reset Week" button cleared ticks (not notes, no confirmation). |
| Other | Filter buttons per day, training notes section, "Open Sheet" link to a Google Sheet, mobile menu. |
| Known issues | Data in one browser only; no history; the week never reset by itself; Lower listed "Back Extension" twice; the sheet URL was duplicated in HTML and JS. |

## What was preserved

- The **exact plan**: all 5 days, 27 exercises, 37 entries, sets/reps and labels are seeded into the
  database by `backend/src/main/resources/db/schema.sql` (verified by `DatabaseIntegrationTest.seedMatchesOriginalWebsite`).
  The two "Back Extension" entries of Lower stay, since they have different prescriptions (3 × 6–10 and 2 × to failure).
- The **look**: colour tokens, orange accent, pill top bar, cards, per-day colours (push/pull/legs/upper/lower),
  breakpoints at 980 px and 680 px. The original `styles.css` is kept as the first half of `frontend/css/style.css`.
- The **start page** `frontend/index.html`: hero, plan summary, weekly flow, day filter buttons, day cards, training
  notes and the Open Sheet link, same markup as before. `frontend/js/home.js` is the original `script.js`
  (moved with `git mv`), rewritten to read the plan from `/api/plan`.

## What changed

| Original | Now |
| --- | --- |
| Tick an exercise | Log real sets (weight × reps, ticked or not) inside a workout session created from a plan day |
| Note per exercise | Workout notes + every set stored with its numbers |
| `localStorage` in one browser | SQLite database on the computer, per user account |
| "X of 37 exercises" bar + manual Reset Week | "X of 5 training days this week", counted from completed workouts; resets every Monday by itself |
| Plan only | Dashboard, history, progress charts, streaks, exercise progression, goals, custom exercises, profile |
| No rules | Every input checked by the Java backend in `backend/` (requirements in `docs/requirements.md`) |
| 3 files | `frontend/` (10 pages, one script each) + `backend/` (Spring Boot) |
| Open `index.html` directly | `cd backend && ./mvnw spring-boot:run`, then http://localhost:8080 |

Removed on purpose: the per-exercise checkbox and note boxes on the plan page, and the Reset Week button. Real
sessions and calendar weeks replace them.
