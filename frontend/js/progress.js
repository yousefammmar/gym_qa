/*
  progress.js: script for progress.html.
  Every number and chart comes from the Java API, which calculates it from the database.
*/

requireLogin();

const picker = document.getElementById("exercisePicker");

async function loadProgress() {
  const [progress, exercises, history] = await Promise.all([
    api("/progress"), api("/exercises"), api("/workouts?status=COMPLETED")
  ]);
  const week = progress.thisWeek;

  // totals
  document.getElementById("totalCompleted").textContent = progress.totalCompletedWorkouts;
  document.getElementById("totalVolume").textContent = formatNumber(progress.totalVolume, 2);
  document.getElementById("currentStreak").textContent = progress.currentStreak;
  document.getElementById("longestStreak").textContent = progress.longestStreak;

  // this week
  document.getElementById("weekStart").textContent = "this week · from " + formatDate(week.weekStart);
  document.getElementById("weekTitle").textContent = week.completedWorkouts + " of " + week.plannedWorkouts + " training days";
  document.getElementById("weekPercent").textContent = week.percent + "%";
  document.getElementById("weekBar").style.width = week.percent + "%";

  // weekly charts
  barChart(document.getElementById("workoutsChart"),
    progress.weekly.map(w => ({ label: shortDate(w.weekStart), value: w.completedWorkouts })),
    { title: "Completed workouts per week", highlightLast: true });
  barChart(document.getElementById("volumeChart"),
    progress.weekly.map(w => ({ label: shortDate(w.weekStart), value: w.volume })),
    { title: "Training volume per week in kg", highlightLast: true });

  // history table
  const historyRows = document.getElementById("historyRows");
  history.forEach(w => {
    const row = fromTemplate("historyRowTemplate");
    row.querySelector("a").href = "workout.html?id=" + w.id;
    fill(row, { date: formatDate(w.workoutDate), name: w.name, sets: w.completedSets, volume: formatKg(w.volume), minutes: w.durationMinutes ?? "–" });
    historyRows.append(row);
  });
  document.getElementById("noHistory").hidden = history.length > 0;

  // exercise picker (?exercise=ID in the address picks one, e.g. from the Exercises page)
  exercises.forEach(exercise => {
    const option = document.createElement("option");
    option.value = exercise.id;
    option.textContent = exercise.name + " · " + label(exercise.muscleGroup);
    picker.append(option);
  });
  const chosen = new URLSearchParams(location.search).get("exercise");
  if (chosen) picker.value = chosen;
  picker.addEventListener("change", loadExercise);
  await loadExercise();
}

/** Heaviest set per completed workout for the chosen exercise. */
async function loadExercise() {
  const points = await api("/progress/exercises/" + picker.value);
  const name = picker.selectedOptions[0].textContent;
  const hasData = points.length > 0;

  document.getElementById("noExerciseData").hidden = hasData;
  document.getElementById("exerciseTable").hidden = !hasData;
  const chart = document.getElementById("exerciseChart");
  if (hasData) {
    lineChart(chart, points.map(p => ({ label: shortDate(p.date), value: p.maxWeightKg })),
      { title: "Heaviest set per workout: " + name, unit: " kg" });
  } else {
    chart.replaceChildren();
  }

  const rows = document.getElementById("exerciseRows");
  rows.replaceChildren();
  points.forEach(p => {
    const row = fromTemplate("exerciseRowTemplate");
    fill(row, { date: formatDate(p.date), heaviest: formatKg(p.maxWeightKg), sets: p.sets, volume: formatKg(p.volume) });
    rows.append(row);
  });
}

loadProgress().catch(err => showMessage(errorText(err), true));
