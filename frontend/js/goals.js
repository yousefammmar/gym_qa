/*
  goals.js: script for goals.html.
  Create goals (POST /api/goals), list them with progress (GET /api/goals),
  edit (PUT), mark completed (POST .../complete) and delete (DELETE).
*/

requireLogin();

const GOAL_TYPES = {
  WORKOUT_COUNT: { text: "Complete a number of workouts", unit: "workouts" },
  TOTAL_VOLUME: { text: "Lift a total volume (kg × reps)", unit: "kg" },
  EXERCISE_MAX_WEIGHT: { text: "Reach a weight on one exercise", unit: "kg" }
};

const goalForm = document.getElementById("goalForm");
const editDialog = document.getElementById("editGoalDialog");
const editForm = document.getElementById("editGoalForm");
let editingGoal = null;

async function setUpForm() {
  document.getElementById("targetDate").min = todayIso();

  const exercises = await api("/exercises");
  const select = document.getElementById("goalExercise");
  exercises.forEach(exercise => {
    const option = document.createElement("option");
    option.value = exercise.id;
    option.textContent = exercise.name;
    select.append(option);
  });

  // the exercise list only matters for weight goals
  goalForm.elements.type.addEventListener("change", () => {
    document.getElementById("exerciseField").hidden = goalForm.elements.type.value !== "EXERCISE_MAX_WEIGHT";
  });
}

goalForm.addEventListener("submit", async event => {
  event.preventDefault();
  const errorBox = goalForm.querySelector(".form-error");
  errorBox.textContent = "";
  const type = goalForm.elements.type.value;
  try {
    await api("/goals", "POST", {
      title: goalForm.elements.title.value,
      type,
      exerciseId: type === "EXERCISE_MAX_WEIGHT" ? Number(goalForm.elements.exerciseId.value) : undefined,
      targetValue: numberOrNothing(goalForm.elements.targetValue.value),
      targetDate: goalForm.elements.targetDate.value || undefined
    });
    goalForm.reset();
    document.getElementById("exerciseField").hidden = true;
    showMessage("Goal created");
    loadGoals();
  } catch (err) {
    showError(errorBox, err);
  }
});

async function loadGoals() {
  const goals = await api("/goals");
  const list = document.getElementById("goalList");
  list.replaceChildren(...goals.map(goalCard));
  document.getElementById("noGoals").hidden = goals.length > 0;
}

function goalCard(goal) {
  const card = fromTemplate("goalCardTemplate");
  const type = GOAL_TYPES[goal.type];
  fill(card, {
    title: goal.title,
    description: type.text + (goal.exerciseName ? ": " + goal.exerciseName : ""),
    numbers: goal.percent + "% · " + formatNumber(goal.currentValue, 2) + " / " + formatNumber(goal.targetValue, 2) + " " + type.unit,
    date: goal.completedAt ? "done " + formatDate(goal.completedAt) : "due " + formatDate(goal.targetDate)
  });
  setPill(card.querySelector('[data-field="state"]'), goal.state);
  card.querySelector(".progress-track span").style.width = goal.percent + "%";

  const completeButton = card.querySelector('[data-action="complete"]');
  const editButton = card.querySelector('[data-action="edit"]');
  if (goal.status === "COMPLETED") {
    completeButton.remove(); // completed goals can only be deleted
    editButton.remove();
  } else {
    completeButton.disabled = goal.percent < 100;
    completeButton.addEventListener("click", () => completeGoal(goal));
    editButton.addEventListener("click", () => openEdit(goal));
  }
  card.querySelector('[data-action="delete"]').addEventListener("click", () => deleteGoal(goal));
  return card;
}

async function completeGoal(goal) {
  try {
    await api("/goals/" + goal.id + "/complete", "POST");
    showMessage("Goal completed!");
    loadGoals();
  } catch (err) {
    showMessage(errorText(err), true);
  }
}

async function deleteGoal(goal) {
  if (!confirm('Delete the goal "' + goal.title + '"?')) return;
  try {
    await api("/goals/" + goal.id, "DELETE");
    loadGoals();
  } catch (err) {
    showMessage(errorText(err), true);
  }
}

function openEdit(goal) {
  editingGoal = goal;
  editForm.elements.title.value = goal.title;
  editForm.elements.targetValue.value = goal.targetValue;
  editForm.elements.targetDate.value = goal.targetDate;
  editForm.querySelector(".form-error").textContent = "";
  editDialog.showModal();
}

document.getElementById("cancelEdit").addEventListener("click", () => editDialog.close());

editForm.addEventListener("submit", async event => {
  event.preventDefault();
  try {
    await api("/goals/" + editingGoal.id, "PUT", {
      title: editForm.elements.title.value,
      targetValue: numberOrNothing(editForm.elements.targetValue.value),
      targetDate: editForm.elements.targetDate.value || undefined
    });
    editDialog.close();
    loadGoals();
  } catch (err) {
    showError(editForm.querySelector(".form-error"), err);
  }
});

Promise.all([setUpForm(), loadGoals()]).catch(err => showMessage(errorText(err), true));
