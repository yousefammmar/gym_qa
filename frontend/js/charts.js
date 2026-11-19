/*
  charts.js: two tiny charts drawn as SVG (no chart library, works offline).

    barChart(container, points, options)   bars, e.g. workouts per week
    lineChart(container, points, options)  line with dots, e.g. heaviest set per workout

  points look like [{ label: "16 Nov", value: 3 }, ...] and always come from the Java API
  (real database records). Colours come from the .chart rules in css/style.css.
*/

const CHART_WIDTH = 640;
const CHART_HEIGHT = 240;
const SVG_NS = "http://www.w3.org/2000/svg";

/** Creates an SVG element with attributes, e.g. svgElement("rect", { x: 1, y: 2 }). */
function svgElement(tag, attributes = {}, text) {
  const element = document.createElementNS(SVG_NS, tag);
  for (const [name, value] of Object.entries(attributes)) element.setAttribute(name, value);
  if (text !== undefined) element.textContent = text;
  return element;
}

/** Empty chart with a title and a text summary for screen readers. */
function newChart(title, points, unit, decimals) {
  const summary = points.map(p => p.label + ": " + formatNumber(p.value, decimals) + unit).join(", ");
  const svg = svgElement("svg", {
    class: "chart",
    viewBox: "0 0 " + CHART_WIDTH + " " + CHART_HEIGHT,
    role: "img",
    "aria-label": title + ". " + summary
  });
  svg.append(svgElement("title", {}, title));
  svg.append(svgElement("line", { class: "axis", x1: 12, x2: CHART_WIDTH - 12, y1: CHART_HEIGHT - 34, y2: CHART_HEIGHT - 34 }));
  return svg;
}

function barChart(container, points, { title, unit = "", decimals = 0, highlightLast = false } = {}) {
  const svg = newChart(title, points, unit, decimals);
  const max = Math.max(1, ...points.map(p => p.value));
  const plotHeight = CHART_HEIGHT - 24 - 34;
  const slot = (CHART_WIDTH - 24) / Math.max(1, points.length);
  const barWidth = Math.min(46, slot * 0.6);

  points.forEach((point, i) => {
    const height = (point.value / max) * plotHeight;
    const x = 12 + i * slot + (slot - barWidth) / 2;
    const y = 24 + plotHeight - height;
    const isCurrent = highlightLast && i === points.length - 1;
    svg.append(svgElement("rect", { class: isCurrent ? "bar current" : "bar", x, y, width: barWidth, height, rx: 8 }));
    if (point.value) {
      svg.append(svgElement("text", { class: "value", x: x + barWidth / 2, y: y - 6, "text-anchor": "middle" }, formatNumber(point.value, decimals)));
    }
    svg.append(svgElement("text", { x: x + barWidth / 2, y: CHART_HEIGHT - 12, "text-anchor": "middle" }, point.label));
  });
  container.replaceChildren(svg);
}

function lineChart(container, points, { title, unit = "" } = {}) {
  const svg = newChart(title, points, unit, 2);
  const values = points.map(p => p.value);
  const max = Math.max(...values);
  const min = Math.min(...values);
  const range = max - min || 1;
  const plotWidth = CHART_WIDTH - 64;
  const plotHeight = CHART_HEIGHT - 24 - 34;
  const step = points.length > 1 ? plotWidth / (points.length - 1) : 0;

  const coords = points.map((point, i) => ({
    point,
    x: 32 + (points.length > 1 ? i * step : plotWidth / 2),
    y: 24 + plotHeight - ((point.value - min) / range) * plotHeight * 0.85 - plotHeight * 0.075
  }));
  svg.append(svgElement("polyline", { class: "line", points: coords.map(c => c.x + "," + c.y).join(" ") }));
  coords.forEach(c => {
    svg.append(svgElement("circle", { class: "dot", cx: c.x, cy: c.y, r: 5 }));
    svg.append(svgElement("text", { class: "value", x: c.x, y: c.y - 10, "text-anchor": "middle" }, formatNumber(c.point.value, 2)));
    svg.append(svgElement("text", { x: c.x, y: CHART_HEIGHT - 12, "text-anchor": "middle" }, c.point.label));
  });
  container.replaceChildren(svg);
}
