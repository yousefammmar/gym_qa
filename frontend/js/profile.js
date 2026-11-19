/*
  profile.js: script for profile.html.
  Loads the account (GET /api/profile), saves details (PUT /api/profile)
  and changes the password (PUT /api/profile/password).
*/

requireLogin();

const profileForm = document.getElementById("profileForm");
const passwordForm = document.getElementById("passwordForm");

async function loadProfile() {
  const user = await api("/profile");
  document.getElementById("username").textContent = user.username;
  document.getElementById("memberSince").textContent = formatDate(user.createdAt);
  profileForm.elements.displayName.value = user.displayName;
  profileForm.elements.email.value = user.email;
}

profileForm.addEventListener("submit", async event => {
  event.preventDefault();
  const errorBox = profileForm.querySelector(".form-error");
  errorBox.textContent = "";
  try {
    await api("/profile", "PUT", Object.fromEntries(new FormData(profileForm)));
    showMessage("Profile saved");
  } catch (err) {
    showError(errorBox, err);
  }
});

passwordForm.addEventListener("submit", async event => {
  event.preventDefault();
  const errorBox = passwordForm.querySelector(".form-error");
  errorBox.textContent = "";
  try {
    await api("/profile/password", "PUT", Object.fromEntries(new FormData(passwordForm)));
    passwordForm.reset();
    showMessage("Password changed");
  } catch (err) {
    showError(errorBox, err);
  }
});

loadProfile().catch(err => showMessage(errorText(err), true));
