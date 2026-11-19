# Button guide

Every button on the website, what it calls and what you should see. Use it as a checklist for manual testing.

| Page | Button / control | Code | API call | You should see | Rule to try |
| --- | --- | --- | --- | --- | --- |
| all | ☰ (phones/tablets) | `setupHeader` (common.js) | – | link list opens/closes | – |
| all (logged in) | Log out | `setupHeader` (common.js) | `POST /api/auth/logout` | home page as a visitor | AUTH-07 |
| index | Start Tracking / Log in | links | – | register / login page | – |
| index | Open Sheet | link, new tab | – | the original Google Sheet (needs internet) | – |
| index | day filter buttons | `renderFilters` (home.js) | – | only that day's card | – |
| index (logged in) | Start *Day* | `startDay` (home.js) | `POST /api/workouts` | workout.html with that day's exercises | WKT-08 |
| login | Log in | login.js | `POST /api/auth/login` | dashboard, or "Wrong username or password (AUTH-06)" | AUTH-06 |
| register | Create account | register.js | `POST /api/auth/register` | dashboard, or the broken rule | AUTH-01…04 |
| dashboard | Start *today* / Resume | dashboard.js | `POST /api/workouts` | the workout page | WKT-08 |
| workouts | Create | workouts.js | `POST /api/workouts` | the new workout | WKT-08 |
| workouts | All / Planned / In progress / Completed | links (`?status=`) | `GET /api/workouts?status=` | filtered history | PRG-06 |
| workout | Start | workout.js | `POST …/start` | status "In progress" | WKT-10 |
| workout | Log set *n* | `logSet` | `POST …/sets` | new set row, totals update, next set pre-filled | WKT-01, 02, 03 |
| workout | weight / reps of a set | `updateSet` | `PUT …/sets/{setId}` | totals update; a bad value shows a red message and is undone | WKT-01, 02 |
| workout | "done" tick | `updateSet` | `PUT …/sets/{setId}` | set dims when un-ticked, volume changes | WKT-05, 12 |
| workout | ✕ next to a set | `deleteSet` | `DELETE …/sets/{setId}` | set removed | WKT-12 |
| workout | ✕ next to an exercise | `removeExercise` (asks first) | `DELETE …/exercises/{blockId}` | exercise removed | WKT-12 |
| workout | Add (exercise) | add form | `POST …/exercises` | new exercise card | WKT-03 |
| workout | Complete workout → Complete | complete dialog | `POST …/complete` | status "Completed" | WKT-04…07, 09 |
| workout | Edit → Save | edit dialog | `PUT /api/workouts/{id}` | new name/date/notes/duration | WKT-04, 08, 09 |
| workout | Delete | `confirm()` | `DELETE /api/workouts/{id}` | back to workouts.html | WKT-11 |
| exercises | search box, muscle group, Search | `loadExercises` | `GET /api/exercises?…` | cards and "n found" | EXR-02, 03 |
| exercises | View progress | link | – | progress.html with that exercise | PRG-05 |
| exercises | Create (custom) | custom form | `POST /api/exercises` | message, card appears | EXR-04…07 |
| progress | Exercise picker | `loadExercise` | `GET /api/progress/exercises/{id}` | line chart + table | PRG-05 |
| goals | Type | goals.js | – | exercise list appears for weight goals | GOL-04 |
| goals | Create goal | goals.js | `POST /api/goals` | new goal card | GOL-01…05 |
| goals | Mark completed (enabled at 100 %) | `completeGoal` | `POST /api/goals/{id}/complete` | state "Completed" | GOL-08 |
| goals | Edit → Save | edit dialog | `PUT /api/goals/{id}` | updated card | GOL-03, 09 |
| goals | Delete | `confirm()` | `DELETE /api/goals/{id}` | card removed | – |
| profile | Save details | profile.js | `PUT /api/profile` | "Profile saved" | AUTH-02, 04, 09 |
| profile | Change password | profile.js | `PUT /api/profile/password` | "Password changed" | AUTH-03, 10 |
