/*
  workout.js: script for workout.html?id=...

  The page always shows the latest workout returned by the server: every action calls the API,
  gets the updated workout back and runs showWorkout() again.

  Sections below: 1 load · 2 show · 3 sets · 4 exercises · 5 start / complete / edit / delete
*/

requireLogin();

const workoutId = new URLSearchParams(location.search).get("id");
let workout = null; // the latest workout from the server

// ---------------------------------------------------------------- 1. load

async function loadWorkout() {
  const [loaded, library] = await Promise.all([api("/workouts/" + workoutId), api("/exercises")]);

  // fill the "Add exercise" list with the exercise library
  const select = document.getElementById("exerciseSelect");
  library.forEach(exercise => {
    const option = document.createElement("option");
    option.value = exercise.id;
    option.textContent = exercise.name + " · " + label(exercise.muscleGroup) + (exercise.custom ? " (custom)" : "");
    select.append(option);
  });

  showWorkout(loaded);
}

// ---------------------------------------------------------------- 2. show

function showWorkout(updated) {
  workout = updated;
  const done = workout.status === "COMPLETED";

  document.title = workout.name + " · Gym Tracker";
  document.getElementById("workoutName").textContent = workout.name;
  setPill(document.getElementById("statusPill"), workout.status);
  document.getElementById("workoutDate").textContent = formatDate(workout.workoutDate);
  document.getElementById("setCount").textContent = workout.completedSets + " completed sets";
  document.getElementById("volume").textContent = formatKg(workout.volume);
  document.getElementById("duration").textContent = workout.durationMinutes ? workout.durationMinutes + " min" : "";
  document.getElementById("workoutNotes").textContent = workout.notes;

  document.getElementById("startButton").hidden = workout.status !== "PLANNED";
  document.getElementById("completeButton").hidden = done;

  const list = document.getElementById("exerciseList");
  list.replaceChildren(...workout.exercises.map(exerciseBlock));
  document.getElementById("noExercises").hidden = workout.exercises.length > 0;
}

/** One exercise card: its logged sets and a "Log set" form. */
function exerciseBlock(block) {
  const card = fromTemplate("exerciseBlockTemplate");
  fill(card, {
    name: block.exerciseName,
    target: label(block.muscleGroup) + " · target " + block.targetSets + " × " + (block.targetReps || "–"),
    logLabel: "Log set " + (block.sets.length + 1)
  });
  card.querySelector('[data-action="remove"]').setAttribute("aria-label", "Remove " + block.exerciseName);
  card.querySelector('[data-action="remove"]').addEventListener("click", () => removeExercise(block));

  const setList = card.querySelector(".set-list");
  block.sets.forEach((set, index) => setList.append(setRow(set, index + 1)));

  // pre-fill the next set with the last one's numbers
  const form = card.querySelector(".log-form");
  const last = block.sets[block.sets.length - 1];
  if (last) {
    form.elements.weightKg.value = last.weightKg;
    form.elements.reps.value = last.reps;
  }
  form.addEventListener("submit", event => {
    event.preventDefault();
    logSet(block, form);
  });
  return card;
}

function setRow(set, number) {
  const row = fromTemplate("setRowTemplate");
  fill(row, { number: "Set " + number });
  if (!set.completed) row.classList.add("not-done");

  const weight = row.querySelector('[name="weightKg"]');
  const reps = row.querySelector('[name="reps"]');
  const tick = row.querySelector('[name="completed"]');
  weight.value = set.weightKg;
  reps.value = set.reps;
  tick.checked = set.completed;

  // editing a field saves it straight away
  weight.addEventListener("change", () => updateSet(set, { weightKg: numberOrNothing(weight.value) }));
  reps.addEventListener("change", () => updateSet(set, { reps: numberOrNothing(reps.value) }));
  tick.addEventListener("change", () => updateSet(set, { completed: tick.checked }));
  row.querySelector('[data-action="delete-set"]').addEventListener("click", () => deleteSet(set));
  return row;
}

/** Shows an error as a red message and puts the last saved values back on the page. */
function failed(err) {
  showMessage(errorText(err), true);
  showWorkout(workout);
}

// ---------------------------------------------------------------- 3. sets

async function logSet(block, form) {
  const errorBox = form.querySelector(".form-error");
  errorBox.textContent = "";
  try {
    const updated = await api("/workouts/" + workoutId + "/sets", "POST", {
      workoutExerciseId: block.id,
      weightKg: numberOrNothing(form.elements.weightKg.value),
      reps: numberOrNothing(form.elements.reps.value)
    });
    showWorkout(updated);
  } catch (err) {
    showError(errorBox, err);
  }
}

function updateSet(set, changes) {
  api("/workouts/" + workoutId + "/sets/" + set.id, "PUT", changes).then(showWorkout).catch(failed);
}

function deleteSet(set) {
  api("/workouts/" + workoutId + "/sets/" + set.id, "DELETE").then(showWorkout).catch(failed);
}

// ---------------------------------------------------------------- 4. exercises

function removeExercise(block) {
  if (!confirm("Remove " + block.exerciseName + " and its sets from this workout?")) return;
  api("/workouts/" + workoutId + "/exercises/" + block.id, "DELETE").then(showWorkout).catch(failed);
}

const addExerciseForm = document.getElementById("addExerciseForm");
addExerciseForm.addEventListener("submit", async event => {
  event.preventDefault();
  const errorBox = addExerciseForm.querySelector(".form-error");
  errorBox.textContent = "";
  try {
    const updated = await api("/workouts/" + workoutId + "/exercises", "POST", {
      exerciseId: Number(addExerciseForm.elements.exerciseId.value),
      targetSets: numberOrNothing(addExerciseForm.elements.targetSets.value),
      targetReps: addExerciseForm.elements.targetReps.value
    });
    showWorkout(updated);
  } catch (err) {
    showError(errorBox, err);
  }
});

// ---------------------------------------------------------------- 5. start / complete / edit / delete

document.getElementById("startButton").addEventListener("click", () => {
  api("/workouts/" + workoutId + "/start", "POST").then(showWorkout).catch(failed);
});

// Cancel buttons inside the dialogs
document.querySelectorAll("[data-close]").forEach(button => {
  button.addEventListener("click", () => button.closest("dialog").close());
});

const completeDialog = document.getElementById("completeDialog");
const completeForm = document.getElementById("completeForm");

document.getElementById("completeButton").addEventListener("click", () => {
  completeForm.querySelector(".form-error").textContent = "";
  if (workout.startedAt) {
    const minutes = Math.max(1, Math.ceil((Date.now() - new Date(workout.startedAt).getTime()) / 60000));
    completeForm.elements.durationMinutes.value = minutes;
    document.getElementById("completeHint").textContent = "Leave empty to use the time since you started.";
  } else {
    completeForm.elements.durationMinutes.value = "";
    document.getElementById("completeHint").textContent = "This workout was never started, so enter how long it took.";
  }
  completeDialog.showModal();
});

completeForm.addEventListener("submit", async event => {
  event.preventDefault();
  try {
    const updated = await api("/workouts/" + workoutId + "/complete", "POST", {
      durationMinutes: numberOrNothing(completeForm.elements.durationMinutes.value)
    });
    completeDialog.close();
    showWorkout(updated);
    showMessage("Workout completed. Nice work!");
  } catch (err) {
    showError(completeForm.querySelector(".form-error"), err);
  }
});

const editDialog = document.getElementById("editDialog");
const editForm = document.getElementById("editForm");

document.getElementById("editButton").addEventListener("click", () => {
  editForm.elements.name.value = workout.name;
  editForm.elements.workoutDate.value = workout.workoutDate;
  editForm.elements.notes.value = workout.notes;
  editForm.elements.durationMinutes.value = workout.durationMinutes ?? "";
  // the duration can only be edited once the workout is completed
  document.getElementById("editDurationField").hidden = workout.status !== "COMPLETED";
  editForm.querySelector(".form-error").textContent = "";
  editDialog.showModal();
});

editForm.addEventListener("submit", async event => {
  event.preventDefault();
  try {
    const updated = await api("/workouts/" + workoutId, "PUT", {
      name: editForm.elements.name.value,
      workoutDate: editForm.elements.workoutDate.value,
      notes: editForm.elements.notes.value,
      durationMinutes: workout.status === "COMPLETED" ? numberOrNothing(editForm.elements.durationMinutes.value) : undefined
    });
    editDialog.close();
    showWorkout(updated);
    showMessage("Workout saved");
  } catch (err) {
    showError(editForm.querySelector(".form-error"), err);
  }
});

document.getElementById("deleteButton").addEventListener("click", async () => {
  if (!confirm('Delete "' + workout.name + '" and all its sets? This cannot be undone.')) return;
  try {
    await api("/workouts/" + workoutId, "DELETE");
    location.href = "workouts.html";
  } catch (err) {
    showMessage(errorText(err), true);
  }
});

loadWorkout().catch(err => showMessage(errorText(err), true));
