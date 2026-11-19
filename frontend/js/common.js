/*
  common.js: shared code used by every page. Load it before the page's own script:
      <script src="js/common.js"></script>
      <script src="js/dashboard.js"></script>

  What is in here:
    1. Login token        getToken, saveToken, clearToken, requireLogin
    2. Talking to Java    api()
    3. Top bar            menu button, Log out link, highlight the current page, show/hide [data-show]
    4. Filling HTML       fill(), fromTemplate(), setPill()
    5. Messages           showMessage(), showError()
    6. Formatting         formatDate(), shortDate(), todayIso(), formatNumber(), formatKg(), label()
    7. Week grid          renderWeekGrid() (used by the home page and the dashboard)

  No libraries, no analytics (QA course project).
*/

// ---------------------------------------------------------------- 1. login token

const TOKEN_KEY = "gym-tracker-token";

function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function saveToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

function clearToken() {
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* storage blocked: nothing to clear */
  }
}

function isLoggedIn() {
  return Boolean(getToken());
}

/** Call at the top of every page that needs an account: sends visitors to the login page. */
function requireLogin() {
  if (!isLoggedIn()) {
    location.href = "login.html";
  }
}

// ---------------------------------------------------------------- 2. talking to the Java backend

/**
 * Calls the REST API and returns the JSON answer.
 * Example: const workout = await api("/workouts", "POST", { planSlug: "push" });
 * On an error it throws an Error with .message (readable text), .code (rule ID such as "WKT-01")
 * and .status (HTTP status). A 401 means the login expired: the user is sent to the login page.
 */
async function api(path, method = "GET", body) {
  const headers = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (getToken()) headers.Authorization = "Bearer " + getToken();

  let response;
  try {
    response = await fetch("/api" + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  } catch {
    throw new Error("Cannot reach the server. Is the Java app still running?");
  }

  if (response.status === 204) return null; // "No Content", e.g. after a delete
  const data = await response.json().catch(() => null);

  if (!response.ok) {
    const error = new Error(data?.message || "Request failed (" + response.status + ")");
    error.code = data?.code;
    error.status = response.status;
    if (response.status === 401 && getToken() && !path.startsWith("/auth/")) {
      clearToken();
      location.href = "login.html";
    }
    throw error;
  }
  return data;
}

// ---------------------------------------------------------------- 3. top bar

function setupHeader() {
  const menuToggle = document.getElementById("menuToggle");
  const navLinks = document.getElementById("navLinks");

  // Menu button (phones/tablets): opens and closes the list of links
  menuToggle?.addEventListener("click", () => {
    const open = navLinks.classList.toggle("open");
    menuToggle.setAttribute("aria-expanded", String(open));
  });

  // Highlight the link of the page we are on
  const currentFile = location.pathname.split("/").pop() || "index.html";
  navLinks?.querySelectorAll("a").forEach(link => {
    if (link.getAttribute("href") === currentFile) {
      link.classList.add("active");
      link.setAttribute("aria-current", "page");
    }
  });

  // Elements marked data-show="guest" or data-show="member" only appear for that kind of visitor
  showForLoginState(document);

  // Log out link: deletes the token on the server and in the browser, then opens the home page
  document.getElementById("logoutLink")?.addEventListener("click", async event => {
    event.preventDefault();
    try {
      await api("/auth/logout", "POST");
    } catch {
      /* token already invalid: still log out here */
    }
    clearToken();
    location.href = "index.html";
  });
}

function showForLoginState(root) {
  const loggedIn = isLoggedIn();
  root.querySelectorAll('[data-show="guest"]').forEach(el => { el.hidden = loggedIn; });
  root.querySelectorAll('[data-show="member"]').forEach(el => { el.hidden = !loggedIn; });
}

// Every page loads its scripts at the end of <body>, so the header already exists here.
setupHeader();

// ---------------------------------------------------------------- 4. filling HTML

/**
 * Puts text into the elements marked with data-field inside `root`.
 * HTML:  <span data-field="name"></span>      JS:  fill(card, { name: "Squats" })
 * textContent is used, so user text can never become HTML (no injection).
 */
function fill(root, values) {
  for (const [field, value] of Object.entries(values)) {
    const element = root.querySelector('[data-field="' + field + '"]');
    if (element) element.textContent = value ?? "";
  }
}

/** Copies the first element inside <template id="..."> so it can be filled and added to the page. */
function fromTemplate(templateId) {
  return document.getElementById(templateId).content.firstElementChild.cloneNode(true);
}

/** Coloured status label, e.g. setPill(span, "IN_PROGRESS") -> "In progress" in orange. */
function setPill(element, status) {
  element.className = "pill " + String(status).toLowerCase();
  element.textContent = label(status);
}

// ---------------------------------------------------------------- 5. messages

/** Short message in the bottom-right corner. */
function showMessage(text, isError = false) {
  let area = document.getElementById("toastArea");
  if (!area) {
    area = document.createElement("div");
    area.id = "toastArea";
    area.className = "toast-area";
    area.setAttribute("role", "status");
    area.setAttribute("aria-live", "polite");
    document.body.append(area);
  }
  const toast = document.createElement("div");
  toast.className = isError ? "toast error" : "toast";
  toast.textContent = text;
  area.append(toast);
  setTimeout(() => toast.remove(), 3500);
}

/** Text for an API error, with its rule code, e.g. "Weight must be from 0 to 500 kg (WKT-02)". */
function errorText(err) {
  return err.code ? err.message + " (" + err.code + ")" : err.message;
}

/** Shows an API error inside a form's <p class="form-error"> element. */
function showError(element, err) {
  element.textContent = errorText(err);
}

// ---------------------------------------------------------------- 6. formatting

/** "2026-11-19" -> "Thu, 19 Nov 2026" (read as a local date, not UTC). */
function formatDate(iso) {
  if (!iso) return "";
  const [y, m, d] = iso.slice(0, 10).split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short", year: "numeric" });
}

/** "2026-11-16" -> "16 Nov" (chart labels). */
function shortDate(iso) {
  const [y, m, d] = iso.slice(0, 10).split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { day: "numeric", month: "short" });
}

/** Today as yyyy-MM-dd, the format of <input type="date">. */
function todayIso() {
  const now = new Date();
  const pad = n => String(n).padStart(2, "0");
  return now.getFullYear() + "-" + pad(now.getMonth() + 1) + "-" + pad(now.getDate());
}

/** 1 = Monday ... 7 = Sunday (same numbering as the plan in the database). */
function todayWeekday() {
  return ((new Date().getDay() + 6) % 7) + 1;
}

function formatNumber(value, decimals = 0) {
  return Number(value ?? 0).toLocaleString(undefined, { maximumFractionDigits: decimals });
}

function formatKg(value) {
  return formatNumber(value, 2) + " kg";
}

/** "IN_PROGRESS" -> "In progress" */
function label(enumValue) {
  const text = String(enumValue ?? "").replaceAll("_", " ").toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** Empty input -> undefined (the server then treats it as "missing"); "12.5" -> 12.5 */
function numberOrNothing(value) {
  if (value === undefined || value === null || String(value).trim() === "") return undefined;
  return Number(value);
}

// ---------------------------------------------------------------- 7. week grid

const WEEKDAY_NAMES = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const SHORT_TAGS = { push: "Push", pull: "Pull", legs: "Legs", upper: "Upper", lower: "Lower" };

/**
 * Fills a .week-grid with 7 cards (Mon..Sun) from the plan. Days without a plan day are rest days.
 * The page must contain <template id="weekCardTemplate">.
 */
function renderWeekGrid(container, plan, highlightWeekday) {
  container.replaceChildren();
  WEEKDAY_NAMES.forEach((dayName, index) => {
    const planDay = plan.find(day => day.weekday === index + 1);
    const card = fromTemplate("weekCardTemplate");
    if (planDay) {
      card.classList.add(planDay.slug);
      fill(card, { tag: SHORT_TAGS[planDay.slug] || planDay.name, day: dayName, title: planDay.name });
    } else {
      card.classList.add("rest");
      fill(card, { tag: "Recovery", day: dayName, title: "Rest / Mobility" });
    }
    if (highlightWeekday === index + 1) card.classList.add("today");
    container.append(card);
  });
}
