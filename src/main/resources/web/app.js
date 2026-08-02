/*
 * RunWild dashboard.
 *
 * No framework and no build step: the page is small enough that the platform covers it,
 * which also means the whole front end is auditable in one file.
 *
 * Accessibility notes:
 *  - every score is rendered as a number and a word, never as colour alone
 *  - the hourly view is a real <table>, so screen readers get the data, not a picture
 *  - rows are selectable by keyboard as well as pointer
 *  - the verdict lives in an aria-live region so changes are announced
 */

const VERDICT_CLASS = {
  'Ideal': 'var(--ideal)',
  'Good': 'var(--good)',
  'Fair': 'var(--fair)',
  'Poor': 'var(--poor)',
  'Stay in': 'var(--stay-in)',
};

const POLLEN_LABELS = {
  '0': 'Not affected',
  '0.5': 'Mild',
  '1': 'Typical',
  '1.5': 'Sensitive',
  '2': 'Severe hay fever',
};

const el = (id) => document.getElementById(id);
const scoreColor = (verdict) => VERDICT_CLASS[verdict] ?? 'var(--accent)';

let currentPlan = null;

/* ------------------------------------------------------------------ fetch */

async function loadPlan() {
  const verdict = el('verdict');
  verdict.setAttribute('aria-busy', 'true');

  const params = new URLSearchParams({
    tempMin: el('tempMin').value,
    tempMax: el('tempMax').value,
    pollen: el('pollen').value,
    duration: el('duration').value,
    dark: el('dark').checked ? 'true' : 'false',
  });

  try {
    const response = await fetch(`/api/plan?${params}`);
    const data = await response.json();
    if (!response.ok) throw new Error(data.detail || 'Request failed');
    currentPlan = data;
    render(data);
  } catch (error) {
    verdict.innerHTML =
      `<p class="error">Could not reach the weather services.</p>
       <p class="verdict-advice">${escapeHtml(error.message)}</p>`;
  } finally {
    verdict.setAttribute('aria-busy', 'false');
  }
}

/* ----------------------------------------------------------------- render */

function render(plan) {
  renderPlace(plan);
  renderVerdict(plan);
  renderAlerts(plan);
  renderWindows(plan);
  renderHours(plan);
  renderFooter(plan);
}

/* Which place, and how fresh — without these the numbers have no provenance. */
function renderPlace(plan) {
  const when = plan.generatedAt.slice(11);
  el('place').textContent = `${plan.location} · as of ${when}`;
}

function renderVerdict(plan) {
  const best = plan.windows[0];
  const verdict = el('verdict');

  if (!best) {
    verdict.innerHTML = `<p class="verdict-headline">No window found</p>
      <p class="verdict-advice">The forecast horizon is shorter than your run.</p>`;
    return;
  }

  const headline = plan.bestIsNow ? 'Go now' : `Best window: ${best.label}`;
  verdict.style.setProperty('--score-color', scoreColor(best.verdict));
  verdict.innerHTML = `
    <p class="verdict-headline">${escapeHtml(headline)}</p>
    <p class="verdict-score"><strong>${best.score}</strong>/100 — ${escapeHtml(best.verdict)}</p>
    ${meter(best.score, `Score ${best.score} out of 100, rated ${best.verdict}`)}
    <p class="verdict-advice">${escapeHtml(best.advice)}</p>`;
}

/*
 * Every active alert is shown, because a Heat Advisory is worth knowing about even
 * though only Severe and Extreme ones cap the score. The severity is spelled out so the
 * distinction is visible rather than implied by the border colour.
 */
function renderAlerts(plan) {
  const container = el('alerts');
  container.hidden = plan.alerts.length === 0;
  container.innerHTML = plan.alerts.map((alert) => `
    <div class="alert${alert.serious ? '' : ' alert--advisory'}" role="note">
      <span class="alert-event">${alert.serious ? '⚠' : 'ⓘ'} ${escapeHtml(alert.event)}</span>
      <span class="alert-severity">(${escapeHtml(alert.severity)})</span>
      <span> — ${escapeHtml(alert.headline)}</span>
    </div>`).join('');
}

function renderWindows(plan) {
  el('windows').innerHTML = plan.windows.map((window) => `
    <li class="window" style="--score-color: ${scoreColor(window.verdict)}">
      <div class="window-head">
        <span class="window-when">${escapeHtml(window.label)}</span>
        <span class="window-score"><strong>${window.score}</strong>/100
          <span class="window-rating">${escapeHtml(window.verdict)}</span></span>
      </div>
      ${meter(window.score, `Score ${window.score} out of 100`)}
      ${window.advisories.length
        ? `<ul class="window-notes">${window.advisories
            .map((a) => `<li>${escapeHtml(a.text)}</li>`).join('')}</ul>`
        : '<p class="window-notes">Clear on every factor we check.</p>'}
    </li>`).join('');
}

function renderHours(plan) {
  el('hours-body').innerHTML = plan.hours.map((hour, index) => `
    <tr tabindex="0" role="button" aria-selected="false" data-index="${index}"
        aria-label="${escapeHtml(`${hour.dayLabel} ${hour.hourLabel}, score ${hour.score} of 100, ${hour.verdict}. ${hour.headline}`)}">
      <th scope="row" class="hour-cell ${hour.daylight ? '' : 'night'}">
        ${escapeHtml(hour.hourLabel)}
      </th>
      <td class="score-cell">
        ${meter(hour.score, '')}
      </td>
      <td>
        <span class="score-number">${hour.score}</span>
        <span class="window-rating">${escapeHtml(hour.verdict)}</span>
      </td>
      <td class="factor-cell">${escapeHtml(hour.headline)}</td>
    </tr>`).join('');

  for (const row of el('hours-body').querySelectorAll('tr')) {
    row.addEventListener('click', () => selectHour(row));
    row.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') selectHour(row);
      if (event.key === ' ') event.preventDefault();
    });
    row.addEventListener('keyup', (event) => {
      if (event.key === ' ') selectHour(row);
    });
  }
}

function selectHour(row) {
  for (const other of el('hours-body').querySelectorAll('tr')) {
    other.setAttribute('aria-selected', String(other === row));
  }
  const hour = currentPlan.hours[Number(row.dataset.index)];
  const section = el('breakdown-section');
  section.hidden = false;

  const rows = hour.penalties.length
    ? hour.penalties
        .slice()
        .sort((a, b) => b.points - a.points)
        .map((p) => `<li><span>${escapeHtml(p.factor)}</span>
                       <span class="breakdown-points">−${p.points}</span></li>`)
        .join('')
    : '<li><span>Nothing deducted</span><span class="breakdown-points">0</span></li>';

  el('breakdown').innerHTML = `
    <p><strong>${escapeHtml(hour.dayLabel)} ${escapeHtml(hour.hourLabel)}</strong> —
       ${hour.score}/100, ${escapeHtml(hour.verdict)}</p>
    <ul class="breakdown-list">${rows}
      <li><span><strong>Conditions</strong></span>
          <span class="breakdown-points">${describeConditions(hour)}</span></li>
    </ul>`;
}

function describeConditions(hour) {
  const parts = [];
  if (hour.apparentF !== null) parts.push(`feels ${hour.apparentF}°F`);
  if (hour.aqi !== null) parts.push(`AQI ${hour.aqi}`);
  if (hour.ozone !== null) parts.push(`ozone ${hour.ozone}`);
  if (hour.uvIndex !== null) parts.push(`UV ${hour.uvIndex}`);
  if (hour.windKph !== null) parts.push(`wind ${hour.windKph} km/h`);
  return escapeHtml(parts.join(' · ') || 'no data');
}

function renderFooter(plan) {
  const t = plan.telemetry;
  el('telemetry').textContent =
    `${t.sources} sources fetched concurrently in ${t.totalMillis} ms · ${t.protocols}` +
    (t.alertsAvailable ? '' : ' · severe-weather alerts unavailable');
  el('coverage').textContent = plan.coverageNotes.join(' ');
}

/* ----------------------------------------------------------------- helpers */

function meter(score, label) {
  const aria = label
    ? `role="img" aria-label="${escapeHtml(label)}"`
    : 'aria-hidden="true"';
  return `<span class="meter" ${aria} style="--score-color: ${scoreColor(verdictOf(score))}">
            <span style="inline-size: ${Math.max(0, Math.min(100, score))}%"></span>
          </span>`;
}

function verdictOf(score) {
  if (score >= 85) return 'Ideal';
  if (score >= 70) return 'Good';
  if (score >= 50) return 'Fair';
  if (score >= 30) return 'Poor';
  return 'Stay in';
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

/* -------------------------------------------------------------------- init */

el('pollen').addEventListener('input', (event) => {
  el('pollen-value').textContent = POLLEN_LABELS[event.target.value] ?? event.target.value;
});

el('settings').addEventListener('submit', (event) => {
  event.preventDefault();
  loadPlan();
});

el('pollen-value').textContent = POLLEN_LABELS[el('pollen').value];
loadPlan();
