/*
  register.js: script for register.html.
  Sends the form to POST /api/auth/register, saves the token and opens the dashboard.
*/

if (isLoggedIn()) location.href = "dashboard.html";

const registerForm = document.getElementById("registerForm");

registerForm.addEventListener("submit", async event => {
  event.preventDefault();
  const errorBox = registerForm.querySelector(".form-error");
  const button = registerForm.querySelector("button[type=submit]");
  errorBox.textContent = "";
  button.disabled = true;
  try {
    const values = Object.fromEntries(new FormData(registerForm));
    const result = await api("/auth/register", "POST", values);
    saveToken(result.token);
    location.href = "dashboard.html";
  } catch (err) {
    showError(errorBox, err);
    button.disabled = false;
  }
});
