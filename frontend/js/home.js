/*
  home.js: script for index.html (this file started as the original website's script.js).

  1. Loads the 5-day plan from GET /api/plan.
  2. Fills the summary numbers, the weekly flow and one card per day (with the filter buttons).
  3. Logged-in users: the Start button on a day creates a workout (POST /api/workouts) and opens it.
*/

let plan = [];
let activeFilter = "All";

async function loadHome() {
  plan = await api("/plan");

  const totalExercises = plan.reduce((sum, day) => sum + day.exercises.length, 0);
  document.getElementById("totalExercises").textContent = totalExercises;
  document.getElementById("trainingDays").textContent = plan.length;
  document.getElementById("recoveryDays").textContent = 7 - plan.length;

  renderWeekGrid(document.getElementById("weekGrid"), plan, isLoggedIn() ? todayWeekday() : null);
  renderFilters();
  renderDays();
}

/** Filter buttons: "All" + one per day. Clicking shows only that day's card. */
function renderFilters() {
  const filters = document.getElementById("filters");
  filters.replaceChildren();
  ["All", ...plan.map(day => day.name)].forEach(name => {
    const button = document.createElement("button");
    button.className = name === activeFilter ? "filter-btn active" : "filter-btn";
    button.textContent = name;
    button.setAttribute("aria-pressed", String(name === activeFilter));
    button.addEventListener("click", () => {
      activeFilter = name;
      renderFilters();
      renderDays();
    });
    filters.append(button);
  });
}

function renderDays() {
  const dayList = document.getElementById("dayList");
  dayList.replaceChildren();
  const visible = activeFilter === "All" ? plan : plan.filter(day => day.name === activeFilter);

  visible.forEach(day => {
    const card = fromTemplate("dayCardTemplate");
    card.classList.add(day.slug); // gives the day its colour (push, pull, legs, upper, lower)
    fill(card, {
      number: plan.indexOf(day) + 1,
      name: day.name,
      focus: day.focus + " · " + WEEKDAY_NAMES[day.weekday - 1],
      prescription: day.prescription,
      startLabel: "Start " + day.name
    });

    const list = card.querySelector(".exercise-grid");
    day.exercises.forEach(exercise => {
      const row = fromTemplate("planExerciseTemplate");
      fill(row, {
        name: exercise.exerciseName,
        sets: exercise.targetSets + " × " + exercise.targetReps,
        meta: exercise.note + " · " + exercise.muscleGroup.toLowerCase()
      });
      list.append(row);
    });

    card.querySelector("button").addEventListener("click", event => startDay(day, event.target));
    showForLoginState(card); // hides the Start button for visitors
    dayList.append(card);
  });
}

async function startDay(day, button) {
  button.disabled = true;
  try {
    const workout = await api("/workouts", "POST", { planSlug: day.slug });
    location.href = "workout.html?id=" + workout.id;
  } catch (err) {
    showMessage(errorText(err), true);
    button.disabled = false;
  }
}

loadHome().catch(err => showMessage(errorText(err), true));
