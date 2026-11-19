/*
  exercises.js: script for exercises.html.
  1. Search + muscle-group filter -> GET /api/exercises?search=...&muscleGroup=...
  2. Custom exercise form -> POST /api/exercises
*/

requireLogin();

const filterForm = document.getElementById("filterForm");
const customForm = document.getElementById("customForm");

async function loadExercises() {
  const params = new URLSearchParams();
  const search = filterForm.elements.search.value.trim();
  const group = filterForm.elements.muscleGroup.value;
  if (search) params.set("search", search);
  if (group) params.set("muscleGroup", group);

  const exercises = await api("/exercises?" + params);

  const results = document.getElementById("results");
  results.replaceChildren();
  exercises.forEach(exercise => {
    const card = fromTemplate("exerciseCardTemplate");
    fill(card, { name: exercise.name, group: label(exercise.muscleGroup), description: exercise.description || "–" });
    card.querySelector('[data-field="custom"]').hidden = !exercise.custom;
    card.querySelector("a").href = "progress.html?exercise=" + exercise.id;
    results.append(card);
  });
  document.getElementById("resultCount").textContent = exercises.length + " found";
  document.getElementById("noResults").hidden = exercises.length > 0;
}

// search while typing (after a short pause), on muscle-group change, and on the Search button
let typingTimer;
filterForm.elements.search.addEventListener("input", () => {
  clearTimeout(typingTimer);
  typingTimer = setTimeout(loadExercises, 250);
});
filterForm.elements.muscleGroup.addEventListener("change", loadExercises);
filterForm.addEventListener("submit", event => {
  event.preventDefault();
  loadExercises();
});

customForm.addEventListener("submit", async event => {
  event.preventDefault();
  const errorBox = customForm.querySelector(".form-error");
  errorBox.textContent = "";
  try {
    const created = await api("/exercises", "POST", Object.fromEntries(new FormData(customForm)));
    customForm.reset();
    showMessage('"' + created.name + '" added');
    loadExercises();
  } catch (err) {
    showError(errorBox, err);
  }
});

loadExercises().catch(err => showMessage(errorText(err), true));
