/*
  dashboard.js: script for dashboard.html.
  Gets everything from GET /api/dashboard (one call) plus GET /api/plan for the week grid.
*/

requireLogin();

async function loadDashboard() {
  const [data, plan] = await Promise.all([api("/dashboard"), api("/plan")]);
  const progress = data.progress;
  const week = progress.thisWeek;

  // greeting and today's plan day
  document.getElementById("todayDate").textContent = formatDate(data.today.date);
  document.getElementById("displayName").textContent = data.displayName;
  document.getElementById("todayText").textContent = data.today.planDay
    ? "Today is " + data.today.planDay.name + ": " + data.today.planDay.focus + "."
    : "Today is a recovery day: walk, stretch and sleep well.";

  if (data.activeWorkout) {
    const resume = document.getElementById("resumeLink");
    resume.href = "workout.html?id=" + data.activeWorkout.id;
    resume.textContent = "Resume " + data.activeWorkout.name;
    resume.hidden = false;
  } else if (data.today.planDay) {
    const start = document.getElementById("startTodayButton");
    start.textContent = "Start " + data.today.planDay.name;
    start.hidden = false;
    start.addEventListener("click", async () => {
      start.disabled = true;
      const workout = await api("/workouts", "POST", { planSlug: data.today.planDay.slug });
      location.href = "workout.html?id=" + workout.id;
    });
  }

  // weekly tracker
  document.getElementById("weekPercent").textContent = week.percent + "%";
  document.getElementById("weekCount").textContent = week.completedWorkouts + " of " + week.plannedWorkouts + " completed";
  document.getElementById("weekBar").style.width = week.percent + "%";

  // numbers
  document.getElementById("totalCompleted").textContent = progress.totalCompletedWorkouts;
  document.getElementById("totalVolume").textContent = formatNumber(progress.totalVolume);
  document.getElementById("currentStreak").textContent = progress.currentStreak;
  document.getElementById("longestStreak").textContent = progress.longestStreak;

  renderWeekGrid(document.getElementById("weekGrid"), plan, data.today.weekday);

  // recent workouts
  const recentList = document.getElementById("recentList");
  data.recentWorkouts.forEach(w => {
    const item = fromTemplate("recentWorkoutTemplate");
    item.querySelector("a").href = "workout.html?id=" + w.id;
    fill(item, { name: w.name, details: formatDate(w.workoutDate) + " · " + w.completedSets + " sets · " + formatKg(w.volume) });
    setPill(item.querySelector('[data-field="status"]'), w.status);
    recentList.append(item);
  });
  document.getElementById("recentEmpty").hidden = data.recentWorkouts.length > 0;

  // active goals
  const goalList = document.getElementById("goalList");
  data.activeGoals.forEach(g => {
    const card = fromTemplate("goalTemplate");
    fill(card, { title: g.title, percent: g.percent + "%", due: "due " + formatDate(g.targetDate) });
    setPill(card.querySelector('[data-field="state"]'), g.state);
    card.querySelector(".progress-track span").style.width = g.percent + "%";
    goalList.append(card);
  });
  document.getElementById("goalEmpty").hidden = data.activeGoals.length > 0;

  barChart(document.getElementById("weeklyChart"),
    progress.weekly.map(w => ({ label: shortDate(w.weekStart), value: w.completedWorkouts })),
    { title: "Completed workouts per week, last 8 weeks", highlightLast: true });
}

loadDashboard().catch(err => showMessage(errorText(err), true));
