/*
  workouts.js: script for workouts.html.
  1. "New workout" form -> POST /api/workouts -> opens workout.html?id=...
  2. History table from GET /api/workouts (optionally ?status=PLANNED / IN_PROGRESS / COMPLETED).
*/

requireLogin();

const newWorkoutForm = document.getElementById("newWorkoutForm");
document.getElementById("workoutDate").value = todayIso();

newWorkoutForm.addEventListener("submit", async event => {
  event.preventDefault();
  try {
    const values = Object.fromEntries(new FormData(newWorkoutForm));
    const workout = await api("/workouts", "POST", values);
    location.href = "workout.html?id=" + workout.id;
  } catch (err) {
    showError(newWorkoutForm.querySelector(".form-error"), err);
  }
});

async function loadHistory() {
  const status = new URLSearchParams(location.search).get("status");
  const workouts = await api(status ? "/workouts?status=" + status : "/workouts");

  // mark the active filter link
  document.querySelectorAll("#statusFilters a").forEach(link => {
    const linkStatus = new URL(link.href).searchParams.get("status");
    link.classList.toggle("active", linkStatus === status);
  });

  const rows = document.getElementById("workoutRows");
  workouts.forEach(w => {
    const row = fromTemplate("workoutRowTemplate");
    row.querySelector("a").href = "workout.html?id=" + w.id;
    fill(row, {
      date: formatDate(w.workoutDate),
      name: w.name,
      exercises: w.exerciseCount,
      sets: w.completedSets,
      volume: formatKg(w.volume),
      minutes: w.durationMinutes ?? "–"
    });
    setPill(row.querySelector('[data-field="status"]'), w.status);
    rows.append(row);
  });
  document.getElementById("emptyMessage").hidden = workouts.length > 0;
}

loadHistory().catch(err => showMessage(errorText(err), true));
