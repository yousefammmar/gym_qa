/*
  login.js: script for login.html.
  Sends the form to POST /api/auth/login, saves the token and opens the dashboard.
*/

if (isLoggedIn()) location.href = "dashboard.html";

const loginForm = document.getElementById("loginForm");

loginForm.addEventListener("submit", async event => {
  event.preventDefault(); // stay on this page; we send the form with fetch instead
  const errorBox = loginForm.querySelector(".form-error");
  const button = loginForm.querySelector("button[type=submit]");
  errorBox.textContent = "";
  button.disabled = true;
  try {
    const values = Object.fromEntries(new FormData(loginForm));
    const result = await api("/auth/login", "POST", values);
    saveToken(result.token);
    location.href = "dashboard.html";
  } catch (err) {
    showError(errorBox, err);
    button.disabled = false;
  }
});
