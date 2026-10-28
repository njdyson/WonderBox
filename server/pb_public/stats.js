// The phone app's stats: what each child watched and how their games went, from
// GET /api/wonder/stats. Loaded after the main script in index.html and uses its helpers
// ($, el, api, face, showError). The events behind it are described in server/EVENTS.md.

let statsDays = Number(localStorage.getItem("wonderbox_stats_days")) || 7;

document.head.append(el("style", { textContent: `
  .range { display: flex; gap: 6px; }
  .range button { flex: 1; }
  .range button:not(.on) { background: rgba(255,255,255,.12); color: var(--text); font-weight: 600; }
  .stat-kid { display: flex; align-items: center; gap: 10px; font-weight: 800; font-size: 18px; }
  .stat-kid .face { width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; font-size: 20px; }
  .tiles { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin-top: 12px; }
  .tile { background: rgba(0,0,0,.18); border-radius: 10px; padding: 10px; }
  .tile b { display: block; font-size: 22px; font-weight: 900; line-height: 1.1; }
  .tile span { font-size: 12px; color: var(--muted); }
  .bars { display: flex; align-items: flex-end; gap: 2px; height: 72px; margin-top: 14px; }
  .bars div { flex: 1; min-width: 0; height: 100%; display: flex; align-items: flex-end; cursor: pointer; }
  .bars i { display: block; width: 100%; border-radius: 4px 4px 0 0; min-height: 2px; }
  .bars div:not(.on) i { opacity: .75; }
  .bar-days { display: flex; gap: 2px; font-size: 11px; color: var(--muted); margin-top: 4px; }
  .bar-days span { flex: 1; min-width: 0; text-align: center; overflow: hidden; }
  .bar-note { font-size: 13px; color: var(--muted); margin-top: 6px; min-height: 18px; }
  .stat-h { font-size: 12px; letter-spacing: 1.5px; color: var(--muted); margin: 16px 0 4px; font-weight: 700; }
  .stat-row { display: flex; gap: 10px; align-items: center; padding: 7px 0; border-top: 1px solid rgba(255,255,255,.08); font-size: 14px; }
  .stat-row:first-of-type { border-top: 0; }
  .stat-row img { width: 56px; aspect-ratio: 16 / 9; object-fit: cover; border-radius: 4px; background: #000; flex: none; }
  .stat-row .t { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .stat-row .n { flex: none; color: var(--muted); text-align: right; }
`}));

/** "1 h 5 min", "12 min", "40 s". */
function duration(seconds) {
  if (!seconds) return "0 min";
  if (seconds < 60) return `${Math.round(seconds)} s`;
  const minutes = Math.round(seconds / 60);
  return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h ${minutes % 60} min`;
}

/** "maths.add" as "Maths add". */
function gameName(id) {
  const s = String(id || "Game").replace(/[._]/g, " ");
  return s.charAt(0).toUpperCase() + s.slice(1);
}

const dayLabel = (date, days) => {
  const d = new Date(date + "T12:00:00");
  return days <= 7 ? d.toLocaleDateString(undefined, { weekday: "short" }) : String(d.getDate());
};

async function loadStats() {
  if (!$("stats")) return;
  try {
    const data = await api(`/api/wonder/stats?days=${statsDays}&tz=${new Date().getTimezoneOffset()}`);
    renderStats(data);
  } catch (e) {
    showError(e);
  }
}

function renderStats(data) {
  const range = el("div", { className: "range" }, ...[[1, "Today"], [7, "7 days"], [30, "30 days"]].map(([d, label]) =>
    el("button", { className: "small" + (d === statsDays ? " on" : ""), textContent: label, onclick: () => {
      statsDays = d;
      localStorage.setItem("wonderbox_stats_days", String(d));
      loadStats();
    } })));
  const cards = data.children.map((c) => statCard(c, data.days));
  $("stats").replaceChildren(range, ...(cards.length ? cards : [el("div", { className: "card empty", textContent: "Add a child to see their stats." })]));
}

function statCard(c, days) {
  const tiles = el("div", { className: "tiles" },
    el("div", { className: "tile" }, el("b", { textContent: duration(c.watchSeconds) }), el("span", { textContent: "watching" })),
    el("div", { className: "tile" }, el("b", { textContent: String(c.plays) }), el("span", { textContent: c.plays === 1 ? "video" : "videos" })),
    el("div", { className: "tile" },
      el("b", { textContent: c.answers ? `${Math.round((100 * c.correct) / c.answers)}%` : "–" }),
      el("span", { textContent: c.answers ? `right of ${c.answers}` : "no quizzes" })));

  const parts = [el("div", { className: "stat-kid" }, face(c), c.name), tiles];
  if (days > 1) parts.push(...dailyChart(c, days));

  if (c.topVideos.length) {
    parts.push(el("div", { className: "stat-h", textContent: "MOST WATCHED" }), ...c.topVideos.slice(0, 5).map((v) => el("div", { className: "stat-row" },
      el("img", { src: `https://i.ytimg.com/vi/${v.youtubeId}/default.jpg`, alt: "", loading: "lazy" }),
      el("div", { className: "t", textContent: v.title }),
      el("div", { className: "n", textContent: `${duration(v.watchSeconds)} · ${v.plays}×` }))));
  }
  const channels = c.topChannels.filter((ch) => ch.channel !== "Other" || c.topChannels.length > 1);
  if (channels.length > 1) {
    parts.push(el("div", { className: "stat-h", textContent: "CHANNELS" }), ...channels.map((ch) => el("div", { className: "stat-row" },
      el("div", { className: "t", textContent: ch.channel }),
      el("div", { className: "n", textContent: `${duration(ch.watchSeconds)} · ${ch.plays}×` }))));
  }
  if (c.games.length) {
    parts.push(el("div", { className: "stat-h", textContent: "GAMES" }), ...c.games.map((g) => el("div", { className: "stat-row" },
      el("div", { className: "t", textContent: g.title || gameName(g.game) }),
      el("div", { className: "n", textContent: `${g.correct}/${g.answers} right` + (g.rounds ? ` · best ${g.bestScore}` : "") }))));
  }
  // Anything else that was logged, so new kinds of activity show up before they get their own section.
  const known = new Set(["video.play", "video.watch", "quiz.answer", "quiz.finish", "profile.pick", "app.start"]);
  const other = Object.entries(c.byType).filter(([type]) => !known.has(type));
  if (other.length) {
    parts.push(el("div", { className: "stat-h", textContent: "OTHER ACTIVITY" }), ...other.map(([type, t]) => el("div", { className: "stat-row" },
      el("div", { className: "t", textContent: gameName(type) }),
      el("div", { className: "n", textContent: `${t.count}×` }))));
  }
  if (!c.plays && !c.answers && !other.length) parts.push(el("div", { className: "empty", textContent: "Nothing yet in this time." }));
  return el("div", { className: "card" }, ...parts);
}

/** Watching time per day as bars in the child's colour; tap a bar for its numbers. */
function dailyChart(c, days) {
  const max = Math.max(60, ...c.daily.map((d) => d.watchSeconds));
  const note = el("div", { className: "bar-note" });
  const describe = (d) => `${new Date(d.date + "T12:00:00").toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "short" })}: ` +
    `${duration(d.watchSeconds)} watching, ${d.plays} ${d.plays === 1 ? "video" : "videos"}` + (d.answers ? `, ${d.correct}/${d.answers} right` : "");
  const bars = el("div", { className: "bars", role: "img", ariaLabel: `${c.name}'s watching time per day` });
  const show = (i) => {
    [...bars.children].forEach((b, j) => b.classList.toggle("on", j === i));
    note.textContent = describe(c.daily[i]);
  };
  c.daily.forEach((d, i) => bars.append(el("div", { title: describe(d), onclick: () => show(i), onmouseenter: () => show(i) },
    el("i", { style: `height:${Math.round((100 * d.watchSeconds) / max)}%;background:${c.color}` }))));
  // A label under every day for a week; every fifth day for a month.
  const labels = el("div", { className: "bar-days" }, ...c.daily.map((d, i) =>
    el("span", { textContent: days <= 7 || i % 5 === 0 || i === c.daily.length - 1 ? dayLabel(d.date, days) : "" })));
  note.textContent = describe(c.daily[c.daily.length - 1]);
  return [bars, labels, note];
}
