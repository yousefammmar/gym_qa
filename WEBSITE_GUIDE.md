# Website guide (for beginners)

The website is in `frontend/` and uses only HTML, CSS and JavaScript. No libraries, no build step and
no analytics (this is a QA project).

## How a page works

Every page is a normal HTML file with all its text, forms and buttons written in the HTML.
At the bottom it loads two scripts:

```html
<script src="js/common.js"></script>   <!-- shared helpers -->
<script src="js/dashboard.js"></script> <!-- this page's code -->
```

The page's script:

1. calls `requireLogin()` (sends visitors to `login.html`),
2. asks the Java server for data with `api("/dashboard")`,
3. puts the data into the page: `element.textContent = ...` for single values, or for lists it copies a
   `<template>` from the HTML and fills its `data-field` parts with `fill(copy, { name: "Squats" })`.

Because text is always set with `textContent`, nothing a user types can turn into HTML.

## Pages

| Page | Script | Server data | Rules shown |
| --- | --- | --- | --- |
| `index.html` home + plan | `home.js` | `GET /api/plan`, `POST /api/workouts` | WKT-08 |
| `login.html` | `login.js` | `POST /api/auth/login` | AUTH-06 |
| `register.html` | `register.js` | `POST /api/auth/register` | AUTH-01…04 |
| `dashboard.html` | `dashboard.js` | `GET /api/dashboard`, `GET /api/plan` | PRG-01…04 |
| `workouts.html` | `workouts.js` | `GET/POST /api/workouts` | WKT-08, PRG-06 |
| `workout.html?id=12` | `workout.js` | `/api/workouts/12/...` | WKT-01…12 |
| `exercises.html` | `exercises.js` | `GET/POST /api/exercises` | EXR-01…07 |
| `progress.html` | `progress.js` | `GET /api/progress`, `/api/progress/exercises/{id}` | PRG-01…07 |
| `goals.html` | `goals.js` | `/api/goals` | GOL-01…10 |
| `profile.html` | `profile.js` | `/api/profile` | AUTH-02, 03, 09, 10 |

## Shared files

| File | Job |
| --- | --- |
| `js/common.js` | login token, `api()` (the only function that talks to the server), top bar (menu, Log out, active link, guest/member parts), `fill()` / `fromTemplate()`, messages, date and number formatting, the week grid |
| `js/charts.js` | bar and line charts drawn as SVG |
| `css/style.css` | 1 colours · 2 the original layout · 3 app parts (forms, tables, dialogs) · 4 phone/tablet rules |

## Common changes

| I want to… | Change |
| --- | --- |
| Change colours | `:root` at the top of `css/style.css` (`--accent` is the orange) |
| Change a text, label or button | the `.html` file of that page |
| Add a menu link | the `<nav>` in every `.html` file (they all have the same top bar) |
| Change the plan | `backend/src/main/resources/db/schema.sql`. Seed rows are only added when missing, so delete `backend/data/gym-tracker.db` afterwards (this deletes logged data) and update `DatabaseIntegrationTest` |
| Change a limit (e.g. max reps) | `backend/.../service/WorkoutValidator.java`, the CHECK in `schema.sql`, the hint in `workout.html`, `docs/requirements.md` and the boundary tests |
| Add a muscle group | `MuscleGroup.java` and the two `<select>` lists in `exercises.html` |

## Notes for testers

- The `min`, `max` and `maxlength` in the HTML only help the user. The Java server checks every rule
  again, so test invalid values against the API too.
- `data-show="guest"` / `data-show="member"` mark parts that only visitors or only logged-in users see.
- The login token is stored in `localStorage` (`gym-tracker-token`). Log out removes it here and on the server.
