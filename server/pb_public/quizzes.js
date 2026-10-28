// The phone app's games: each child's maths and spelling setup, and the quizzes the parent
// writes. Loaded after the main script in index.html and uses its helpers ($, el, api, edit,
// seg, face, toast, showError, openSheet, closeSheet, photoUrl, library, selected, child).
// The server checks everything (pb_hooks/quizzes_lib.js); the TV makes up the questions.

document.head.append(el("style", { textContent: `
  .game { display: grid; gap: 10px; padding: 12px 0; border-top: 1px solid rgba(255,255,255,.08); }
  .game:first-child { border-top: 0; padding-top: 0; }
  .game-head { display: flex; align-items: center; gap: 10px; font-weight: 800; font-size: 18px; }
  .game-head .grow { min-width: 0; }
  .switch { position: relative; width: 50px; height: 30px; border-radius: 999px; padding: 0; flex: none;
            background: rgba(255,255,255,.2); transition: background .15s; }
  .switch::after { content: ""; position: absolute; top: 3px; left: 3px; width: 24px; height: 24px; border-radius: 50%;
                   background: #fff; transition: transform .15s; }
  .switch.on { background: var(--good); }
  .switch.on::after { transform: translateX(20px); }
  .chips { display: flex; gap: 6px; flex-wrap: wrap; }
  .chips button { background: rgba(255,255,255,.12); color: var(--text); font-weight: 600; padding: 7px 11px; border-radius: 999px; font-size: 14px; }
  .chips button.on { background: var(--text); color: var(--deep); font-weight: 800; }
  .chips.nums button { min-width: 40px; padding: 7px 0; }
  .sub-h { font-size: 13px; font-weight: 700; color: var(--muted); margin-top: 2px; }
  .more { background: none; color: var(--accent, var(--yellow)); padding: 0; font-weight: 700; text-align: left; }
  .words { display: flex; gap: 6px; flex-wrap: wrap; }
  .word { display: inline-flex; align-items: center; gap: 4px; background: rgba(255,255,255,.12); border-radius: 999px; padding: 3px 4px 3px 10px; font-weight: 700; }
  .word button { background: none; color: var(--text); padding: 2px 6px; font-size: 14px; }
  .word .pic { width: 24px; height: 24px; font-size: 18px; }
  .pic { display: inline-grid; place-items: center; overflow: hidden; flex: none; }
  .pic img { width: 100%; height: 100%; object-fit: contain; }
  .quiz-row { display: flex; gap: 10px; align-items: center; padding: 8px 0; border-top: 1px solid rgba(255,255,255,.08); }
  .quiz-row:first-child { border-top: 0; }
  .quiz-row .pic { width: 44px; height: 44px; font-size: 34px; }
  .quiz-row .t { font-weight: 800; }
  .qcard { background: rgba(0,0,0,.18); border-radius: 12px; padding: 12px; display: grid; gap: 8px; }
  .qcard textarea, .qcard input[type=text], .sheet textarea {
    width: 100%; font: inherit; font-size: 16px; padding: 10px 12px; border-radius: 10px; border: 2px solid transparent; background: #fff; color: #111; resize: vertical; }
  .answer { display: flex; gap: 6px; align-items: center; }
  .answer input[type=text] { flex: 1; min-width: 0; }
  .tick { width: 38px; height: 38px; border-radius: 50%; padding: 0; flex: none; background: rgba(255,255,255,.12); color: var(--text); font-size: 18px; }
  .tick.on { background: var(--good); color: var(--deep); }
  .picbtn { width: 46px; height: 46px; padding: 0; flex: none; background: rgba(255,255,255,.12); border-radius: 10px; font-size: 28px; display: grid; place-items: center; overflow: hidden; }
  .picbtn.big { width: 84px; height: 84px; font-size: 56px; }
  .picbtn.empty { font-size: 13px; color: var(--muted); font-weight: 600; line-height: 1.1; }
  .picbtn img { width: 100%; height: 100%; object-fit: contain; }
  #picker { position: fixed; inset: 0; z-index: 30; }
  #picker .sheet { z-index: 31; max-height: 92vh; }
  #picker .scrim { z-index: 30; }
  .clip-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 6px; }
  .clip-grid button { aspect-ratio: 1; padding: 4px; background: rgba(255,255,255,.1); border-radius: 10px; }
  .clip-grid img { width: 100%; height: 100%; object-fit: contain; }
  .sheet-error { background: #6b1f3a; border-radius: 10px; padding: 10px 12px; }
`}));

const ROUND_SIZES = [3, 5, 10, 15, 20, 25, 30];
// Photo jigsaws on the TV: pieces along each side (app/.../games/Jigsaw.kt). 0 is off.
const JIGSAW_SIZES = [2, 3, 4, 5];

// The TV's levels: the same setups as pb_hooks/quizzes_lib.js and app/.../games/Setup.kt.
const MATHS_LEVELS = [
  ["Counting to 5", { types: ["count"], max: 5, choices: 3, pictures: true }],
  ["Adding to 5", { types: ["add"], max: 5, choices: 3, pictures: true }],
  ["Adding to 10", { types: ["add"], max: 10, choices: 4, pictures: false }],
  ["Adding and taking away to 10", { types: ["add", "sub"], max: 10, choices: 4, pictures: false }],
  ["Adding and taking away to 20", { types: ["add", "sub"], max: 20, choices: 4, pictures: false }],
  ["2, 5 and 10 times tables", { types: ["times"], max: 10, tables: [2, 5, 10], choices: 4, pictures: false }],
];
const SPELLING_LEVELS = [
  ["First letter (_at)", { types: ["first"], lists: ["short"], choices: 3 }],
  ["Middle letter (c_t)", { types: ["missing"], lists: ["short"], choices: 3 }],
  ["Missing letter, longer words", { types: ["missing"], lists: ["long"], choices: 4 }],
  ["Which is spelt right?", { types: ["pick"], lists: ["short", "long"], choices: 3 }],
  ["Spell the whole word", { types: ["build"], lists: ["long"], choices: 4 }],
];
const CLOCK_LEVELS = [
  ["O'clock", { types: ["oclock"], choices: 3 }],
  ["O'clock and half past", { types: ["oclock", "half"], choices: 3 }],
  ["Quarter past and quarter to", { types: ["oclock", "half", "quarter"], choices: 4 }],
  ["Five minutes (20 past 3)", { types: ["five"], choices: 4 }],
  ["Any minute", { types: ["minute"], choices: 4 }],
];
const MATHS_TYPES = [["count", "Counting"], ["add", "Adding"], ["sub", "Taking away"], ["add3", "Adding 3 numbers"], ["missing", "Missing number (3 + _ = 5)"],
  ["bonds", "Number bonds (7 + _ = 10)"], ["double", "Doubles"], ["half", "Halves"], ["more", "1 or 10 more and less"], ["bigger", "Biggest number"], ["times", "Times tables"]];
const SPELLING_TYPES = [["first", "First letter"], ["missing", "Missing letter"], ["pick", "Which is spelt right?"], ["build", "Spell the whole word"],
  ["hear", "Hear it, pick the spelling"], ["hearbuild", "Hear it, spell it"]];
const CLOCK_TYPES = [["oclock", "O'clock"], ["half", "Half past"], ["quarter", "Quarter past and to"], ["five", "Five minutes"], ["minute", "Any minute"]];
// The games that can win a treat (pb_hooks/quizzes_lib.js), and the ones that do until changed.
const TREAT_GAMES = [["maths", "Maths"], ["spelling", "Spelling"], ["clock", "Clock"], ["quizzes", "My quizzes"], ["hangman", "Hangman"], ["memory", "Memory pairs"], ["jigsaw", "Jigsaws"]];
const TREAT_DEFAULT = ["maths", "spelling", "clock", "quizzes", "hangman"];
const MAXES = [5, 10, 20, 50, 100];

const sameList = (a, b) => JSON.stringify(a) === JSON.stringify(b);

function mathsLevelOf(s) {
  const key = (x) => JSON.stringify([x.types, x.max, x.types.includes("times") ? (x.tables || [2, 5, 10]) : [], x.choices, x.pictures]);
  return MATHS_LEVELS.findIndex(([, p]) => key(p) === key(s)) + 1;
}

function spellingLevelOf(s) {
  if (s.lists.includes("mine")) return 0;
  const key = (x) => JSON.stringify([x.types, x.lists, x.types.every((t) => t === "build") ? 0 : x.choices]);
  return SPELLING_LEVELS.findIndex(([, p]) => key(p) === key(s)) + 1;
}

function clockLevelOf(s) {
  const key = (x) => JSON.stringify([x.types, x.choices]);
  return CLOCK_LEVELS.findIndex(([, p]) => key(p) === key(s)) + 1;
}

// Which children's "change what it asks" parts are open, by "<child>/<game>".
const openSetups = new Set();

/** A picture: an emoji, or a protected quiz image (fetched with the sign-in, like the photos). */
function pic(picture, className = "pic") {
  const node = el("span", { className });
  if (isImage(picture)) {
    const img = el("img", { alt: "" });
    photoUrl(picture).then((url) => { img.src = url; }).catch(() => {});
    node.append(img);
  } else {
    node.textContent = picture || "";
  }
  return node;
}

const isImage = (p) => String(p || "").startsWith("/api/");

/** Toggle buttons, several on at once: [options] as [value, label]. */
function chips(options, values, onChange, className = "chips") {
  return el("div", { className }, ...options.map(([v, label]) => el("button", {
    type: "button", className: values.includes(v) ? "on" : "", textContent: label,
    onclick: () => onChange(values.includes(v) ? values.filter((x) => x !== v) : [...values, v]),
  })));
}

const toggle = (on, onChange, title) =>
  el("button", { type: "button", className: "switch" + (on ? " on" : ""), title, "aria-pressed": String(on), onclick: () => onChange(!on) });

// --- Each child's games ------------------------------------------------------

/** A child's games, saved as soon as anything changes. */
function renderGames(c) {
  $("gamesHeading").hidden = $("games").hidden = !c;
  if (!c) return;
  $("gamesHeading").textContent = `${c.name.toUpperCase()}'S GAMES`;
  const g = c.games || {};
  const maths = g.mathsSetup || { on: true, ...MATHS_LEVELS[(g.maths || 1) - 1][1], tables: [2, 5, 10] };
  const spelling = g.spellingSetup || { on: true, ...SPELLING_LEVELS[(g.spelling || 1) - 1][1], myWords: [] };
  const clock = g.clockSetup || { on: true, ...CLOCK_LEVELS[(g.clock || 1) - 1][1] };
  const save = (change) => edit("/api/wonder/children/games", { id: c.id, ...change }).then((d) => d && toast("Saved"));
  const saveMaths = (change) => save({ mathsSetup: { ...maths, ...change } });
  const saveSpelling = (change) => save({ spellingSetup: { ...spelling, ...change } });
  const saveClock = (change) => save({ clockSetup: { ...clock, ...change } });
  const select = (options, value, onChange) => {
    const s = el("select", { onchange: () => onChange(Number(s.value)) }, ...options.map(([v, label]) => el("option", { value: v, textContent: label, selected: v === value })));
    return s;
  };
  const hasTreats = library.videos.concat(library.channels).some((i) => i.tier === "treat" && i.children.includes(c.id));

  // Maths
  const mathsLevel = mathsLevelOf(maths);
  const mathsKey = `${c.id}/maths`;
  const mathsOpen = openSetups.has(mathsKey) || !mathsLevel;
  const mathsPart = el("div", { className: "game" },
    el("div", { className: "game-head" }, el("span", { className: "grow", textContent: "🔢 Maths" }),
      toggle(maths.on, (on) => saveMaths({ on }), "Show Maths on the TV")));
  if (maths.on) {
    mathsPart.append(
      select([...(mathsLevel ? [] : [[0, "Your own mix"]]), ...MATHS_LEVELS.map(([n], i) => [i + 1, `Level ${i + 1}: ${n}`])], mathsLevel,
        (v) => v && saveMaths({ ...MATHS_LEVELS[v - 1][1], tables: MATHS_LEVELS[v - 1][1].tables || maths.tables })),
      el("button", { type: "button", className: "more", textContent: mathsOpen ? "Hide the details ▴" : "Change what it asks ▾", onclick: () => {
        openSetups[mathsOpen ? "delete" : "add"](mathsKey);
        renderGames(c);
      } }));
    if (mathsOpen) {
      mathsPart.append(
        el("div", { className: "sub-h", textContent: "Kinds of question (mixed together)" }),
        chips(MATHS_TYPES, maths.types, (types) => types.length && saveMaths({ types })),
        ...(maths.types.some((t) => t !== "times") ? [
          el("div", { className: "sub-h", textContent: "Numbers up to" }),
          seg(MAXES.map((m) => [m, String(m)]), maths.max, (max) => saveMaths({ max })),
        ] : []),
        ...(maths.types.includes("times") ? [
          el("div", { className: "sub-h", textContent: "Times tables" }),
          chips([...Array(12)].map((_, i) => [i + 1, String(i + 1)]), maths.tables, (tables) => tables.length && saveMaths({ tables: tables.sort((a, b) => a - b) }), "chips nums"),
        ] : []),
        el("div", { className: "sub-h", textContent: "Answers to pick from" }),
        seg([2, 3, 4].map((n) => [n, String(n)]), maths.choices, (choices) => saveMaths({ choices })),
        ...(maths.max <= 10 && maths.types.some((t) => t === "add" || t === "sub") ? [
          el("div", { className: "row" }, el("span", { className: "grow", textContent: "Draw things to count" }),
            toggle(maths.pictures, (pictures) => saveMaths({ pictures }))),
        ] : []));
    }
  }

  // Spelling
  const spellingLevel = spellingLevelOf(spelling);
  const spellingKey = `${c.id}/spelling`;
  const spellingOpen = openSetups.has(spellingKey) || !spellingLevel;
  const spellingPart = el("div", { className: "game" },
    el("div", { className: "game-head" }, el("span", { className: "grow", textContent: "🔤 Spelling" }),
      toggle(spelling.on, (on) => saveSpelling({ on }), "Show Spelling on the TV")));
  if (spelling.on) {
    const words = spelling.myWords || [];
    spellingPart.append(
      select([...(spellingLevel ? [] : [[0, "Your own mix"]]), ...SPELLING_LEVELS.map(([n], i) => [i + 1, `Level ${i + 1}: ${n}`])], spellingLevel,
        (v) => v && saveSpelling({ ...SPELLING_LEVELS[v - 1][1] })),
      el("button", { type: "button", className: "more", textContent: spellingOpen ? "Hide the details ▴" : "Change what it asks ▾", onclick: () => {
        openSetups[spellingOpen ? "delete" : "add"](spellingKey);
        renderGames(c);
      } }));
    if (spellingOpen) {
      const wordInput = el("input", { type: "text", placeholder: "Add words, e.g. said, because, friend", autocapitalize: "off", autocomplete: "off", enterkeyhint: "done" });
      const addWords = () => {
        const fresh = wordInput.value.toLowerCase().split(/[^a-z]+/).filter((w) => w.length >= 2 && w.length <= 12 && !words.some((x) => x.word === w));
        if (!fresh.length) return;
        // The first words added switch "My words" on.
        const lists = spelling.lists.includes("mine") ? spelling.lists : [...spelling.lists, "mine"];
        saveSpelling({ myWords: [...words, ...fresh.map((word) => ({ word, picture: "" }))], lists });
      };
      wordInput.addEventListener("keydown", (e) => { if (e.key === "Enter") { e.preventDefault(); addWords(); } });
      spellingPart.append(
        el("div", { className: "sub-h", textContent: "Kinds of question (mixed together)" }),
        chips(SPELLING_TYPES, spelling.types, (types) => types.length && saveSpelling({ types })),
        el("div", { className: "sub-h", textContent: "Which words" }),
        chips([["short", "Short words (cat, dog)"], ["long", "Longer words (frog, house)"], ...(words.length ? [["mine", `My words (${words.length})`]] : [])],
          spelling.lists, (lists) => lists.length && saveSpelling({ lists })),
        ...(spelling.types.some((t) => t !== "build") ? [
          el("div", { className: "sub-h", textContent: "Answers to pick from" }),
          seg([2, 3, 4].map((n) => [n, String(n)]), spelling.choices, (choices) => saveSpelling({ choices })),
        ] : []),
        el("div", { className: "sub-h", textContent: "My words: spellings from school, names, anything. Tap one to give it a picture." }),
        words.length ? el("div", { className: "words" }, ...words.map((w) => el("span", { className: "word" },
          w.picture ? pic(w.picture) : "",
          el("span", { textContent: w.word, onclick: () => pickPicture(w.word, (picture) =>
            saveSpelling({ myWords: words.map((x) => (x.word === w.word ? { ...x, picture } : x)) }), w.picture) }),
          el("button", { type: "button", textContent: "✕", title: `Remove ${w.word}`, onclick: () =>
            saveSpelling({ myWords: words.filter((x) => x.word !== w.word) }) })))) : "",
        el("div", { className: "row" }, wordInput, el("button", { type: "button", textContent: "Add", onclick: addWords })),
        words.length > 8 && words.some((w) => w.word.length > 8)
          ? el("div", { className: "muted", textContent: "Words longer than 8 letters are asked as a missing letter instead of spelt letter by letter." })
          : "");
    }
  }

  // Telling the time
  const clockLevel = clockLevelOf(clock);
  const clockKey = `${c.id}/clock`;
  const clockOpen = openSetups.has(clockKey) || !clockLevel;
  const clockPart = el("div", { className: "game" },
    el("div", { className: "game-head" }, el("span", { className: "grow", textContent: "🕒 Clock" }),
      toggle(clock.on, (on) => saveClock({ on }), "Show the clock game on the TV")));
  if (clock.on) {
    clockPart.append(
      select([...(clockLevel ? [] : [[0, "Your own mix"]]), ...CLOCK_LEVELS.map(([n], i) => [i + 1, `Level ${i + 1}: ${n}`])], clockLevel,
        (v) => v && saveClock({ ...CLOCK_LEVELS[v - 1][1] })),
      el("button", { type: "button", className: "more", textContent: clockOpen ? "Hide the details ▴" : "Change what it asks ▾", onclick: () => {
        openSetups[clockOpen ? "delete" : "add"](clockKey);
        renderGames(c);
      } }));
    if (clockOpen) {
      clockPart.append(
        el("div", { className: "sub-h", textContent: "Which times (mixed together)" }),
        chips(CLOCK_TYPES, clock.types, (types) => types.length && saveClock({ types })),
        el("div", { className: "sub-h", textContent: "Answers to pick from" }),
        seg([2, 3, 4].map((n) => [n, String(n)]), clock.choices, (choices) => saveClock({ choices })),
        el("div", { className: "muted", textContent: "A clock face with both hands; the answers are said the British way: quarter past 3, 20 to 5." }));
    }
  }

  // Photo jigsaws
  const jigsaw = g.jigsaw ?? 3;
  const jigsawPart = el("div", { className: "game" },
    el("div", { className: "game-head" }, el("span", { className: "grow", textContent: "🧩 Photo jigsaws" }),
      toggle(jigsaw > 0, (on) => save({ jigsaw: on ? 3 : 0 }), "Show jigsaws of the family photos on the TV")));
  if (jigsaw > 0) {
    jigsawPart.append(
      el("div", { className: "sub-h", textContent: "Pieces" }),
      seg(JIGSAW_SIZES.map((n) => [n, `${n} × ${n}`]), jigsaw, (v) => save({ jigsaw: v })),
      el("div", { className: "muted", textContent: `${jigsaw * jigsaw} pieces. ${c.name} picks a photo, then swaps pieces with the arrows and OK. Shows once you've added photos.` }));
  }

  // Sound effects (app/.../ui/Sounds.kt)
  const sounds = g.sounds || "silly";
  const soundsPart = el("div", { className: "game" },
    el("div", { className: "game-head" }, el("span", { className: "grow", textContent: "🔊 Game sounds" })),
    seg([["off", "Off"], ["calm", "Calm"], ["silly", "Silly"]], sounds, (v) => save({ sounds: v })),
    el("div", { className: "muted", textContent: {
      off: "No sound effects, just the voice.",
      calm: "Soft blips and chimes for moving, right answers and finishing.",
      silly: "Boings, squeaks and farts for right answers, and a silly noise to try again. Wrong answers never get a fart.",
    }[sounds] }));

  // Which games win a treat: the learning ones by default, so it isn't all jigsaws.
  const treatGames = g.treatGames || TREAT_DEFAULT;
  const treatsPart = el("div", { className: "game" },
    el("div", { className: "game-head" }, el("span", { className: "grow", textContent: "⭐ Games that win a treat" })),
    chips(TREAT_GAMES, treatGames, (list) => save({ treatGames: list })),
    el("div", { className: "muted", textContent: treatGames.length
      ? "The others are still there to play, just for fun. Games that win a treat have a ⭐ on the TV."
      : "No game wins a treat, so treats stay locked." }));

  $("games").replaceChildren(
    mathsPart,
    spellingPart,
    clockPart,
    jigsawPart,
    soundsPart,
    treatsPart,
    el("label", { className: "field" }, "Right answers to win a treat",
      select(ROUND_SIZES.map((n) => [n, `${n}`]), g.roundSize || 10, (v) => save({ roundSize: v }))),
    el("div", { className: "muted", textContent: hasTreats
      ? TIER_HELP + " A quiz is won by answering all its questions."
      : `Nothing of ${c.name}'s is a treat yet, so games are just for fun. Mark a video or channel as a treat to make it a prize.` }));
}

// --- Quizzes -----------------------------------------------------------------

function renderQuizzes() {
  const c = child(selected);
  const list = library.quizzes || [];
  $("quizzes").replaceChildren(
    ...(list.length ? [el("div", {}, ...list.map((q, i) => el("div", { className: "quiz-row" },
      pic(q.icon || "❓"),
      el("div", { className: "grow", onclick: () => quizSheet(q) },
        el("div", { className: "t", textContent: q.title }),
        el("div", { className: "badges" },
          ...q.children.map(child).filter(Boolean).map((k) => face(k, "span", "face-mini")),
          el("span", { className: "badge", textContent: `${q.questions.length} question${q.questions.length === 1 ? "" : "s"}` }),
          ...(q.children.length ? [] : [el("span", { className: "badge", textContent: "Not on the TV yet" })]))),
      el("div", { className: "tools" },
        el("button", { className: "ghost", textContent: "↑", disabled: i === 0, onclick: () => edit("/api/wonder/quizzes/move", { id: q.id, delta: -1 }) }),
        el("button", { className: "ghost", textContent: "↓", disabled: i === list.length - 1, onclick: () => edit("/api/wonder/quizzes/move", { id: q.id, delta: 1 }) })))))]
      : [el("div", { className: "muted", textContent: "Make your own multiple-choice quizzes: questions with two to four answers, in words or pictures. Each one shows on the TV next to Maths and Spelling." })]),
    el("button", { type: "button", textContent: "+ New quiz", onclick: () => quizSheet(null, c) }));
}

const blankQuestion = () => ({ text: "", picture: "", answers: [{ text: "", picture: "" }, { text: "", picture: "" }], correct: 0 });

/** Write a new quiz ([quiz] null, for child [forChild]) or change one. */
function quizSheet(quiz, forChild) {
  const draft = quiz
    ? JSON.parse(JSON.stringify(quiz))
    : { title: "", icon: "", children: forChild ? [forChild.id] : [], shuffle: true, questions: [blankQuestion()] };
  const initial = JSON.stringify(draft);
  let error = "";
  let focusLast = false;

  const draw = () => {
    const scroll = document.querySelector("#sheet .sheet")?.scrollTop || 0;
    const title = el("input", { type: "text", value: draft.title, placeholder: "Quiz name, e.g. Animal sounds", maxLength: 60, oninput: () => { draft.title = title.value; } });
    openSheet(
      el("div", { style: "font-weight:800;font-size:20px", textContent: quiz ? "Change quiz" : "New quiz" }),
      el("div", { className: "row" },
        picButton(draft.icon || "❓", "big", () => pickPicture(draft.title || "the quiz", (p) => { draft.icon = p; draw(); }, draft.icon)),
        el("div", { className: "grow stack" }, title,
          el("div", { className: "muted", textContent: "The picture is the quiz's tile on the TV." }))),
      el("div", { style: "font-weight:700", textContent: "Who's it for?" }),
      library.children.length
        ? el("div", { className: "kids" }, ...library.children.map((k) => {
            const on = draft.children.includes(k.id);
            return el("button", { type: "button", className: "kid" + (on ? " on" : ""), onclick: () => {
              draft.children = on ? draft.children.filter((id) => id !== k.id) : [...draft.children, k.id];
              draw();
            } }, face(k), k.name);
          }))
        : el("div", { className: "muted", textContent: "Add a child first." }),
      el("div", { className: "row" }, el("span", { className: "grow", textContent: "Ask in a different order each time" }),
        toggle(draft.shuffle, (on) => { draft.shuffle = on; draw(); })),
      el("div", { className: "muted", textContent: "The TV reads each question out, and each answer as it's picked. Answers are always shuffled, so put them in any order and tick the right one." }),
      ...draft.questions.map((q, n) => questionCard(q, n, draw)),
      el("button", { type: "button", className: "ghost", textContent: "+ Add a question", disabled: draft.questions.length >= 100, onclick: () => {
        draft.questions.push(blankQuestion());
        focusLast = true;
        draw();
      } }),
      error ? el("div", { className: "sheet-error", textContent: error }) : "",
      el("button", { type: "button", textContent: quiz ? "Save" : "Save quiz", onclick: async (e) => {
        e.target.disabled = true;
        try {
          const data = await api("/api/wonder/quizzes/save", { id: quiz && quiz.id, ...draft });
          setLibrary(data);
          toast(draft.children.length ? "Saved. It's on the TV." : "Saved");
          closeSheet();
        } catch (err) {
          if (err instanceof AuthError) return signedOut();
          error = err.message;
          draw();
          const sheet = document.querySelector("#sheet .sheet");
          sheet.scrollTop = sheet.scrollHeight;
        }
      } }),
      ...(quiz ? [el("button", { type: "button", className: "ghost", textContent: "Delete quiz", onclick: async () => {
        if (!confirm(`Delete "${quiz.title}"?`)) return;
        if (await edit("/api/wonder/quizzes/remove", { id: quiz.id })) closeSheet();
      } })] : []),
      el("button", { type: "button", className: "ghost", textContent: "Cancel", onclick: () => {
        if (JSON.stringify(draft) !== initial && !confirm("Close without saving?")) return;
        closeSheet();
      } }));
    const sheet = document.querySelector("#sheet .sheet");
    if (focusLast) {
      focusLast = false;
      const boxes = sheet.querySelectorAll(".qcard textarea");
      boxes[boxes.length - 1]?.scrollIntoView({ block: "center" });
      boxes[boxes.length - 1]?.focus();
    } else {
      sheet.scrollTop = scroll;
    }
    if (!quiz && !draft.title) setTimeout(() => title.focus(), 50);
  };

  /** One question: its words, an optional picture, and its 2 to 4 answers. */
  const questionCard = (q, n, redraw) => {
    const text = el("textarea", { rows: 2, value: q.text, placeholder: "Question, e.g. Which animal says moo?", maxLength: 200, oninput: () => { q.text = text.value; } });
    const move = (delta) => {
      const list = draft.questions;
      list.splice(n + delta, 0, list.splice(n, 1)[0]);
      redraw();
    };
    return el("div", { className: "qcard" },
      el("div", { className: "row" },
        el("b", { className: "grow", textContent: `Question ${n + 1}` }),
        el("button", { type: "button", className: "ghost small", textContent: "↑", disabled: n === 0, onclick: () => move(-1) }),
        el("button", { type: "button", className: "ghost small", textContent: "↓", disabled: n === draft.questions.length - 1, onclick: () => move(1) }),
        el("button", { type: "button", className: "ghost small", textContent: "✕", title: "Remove this question", disabled: draft.questions.length === 1, onclick: () => {
          draft.questions.splice(n, 1);
          redraw();
        } })),
      el("div", { className: "row" },
        picButton(q.picture, "", () => pickPicture(q.text || `question ${n + 1}`, (p) => { q.picture = p; redraw(); }, q.picture), "Picture"),
        text),
      ...q.answers.map((a, i) => {
        const input = el("input", { type: "text", value: a.text, placeholder: i === q.correct ? "Right answer" : "Answer", maxLength: 60, oninput: () => { a.text = input.value; } });
        return el("div", { className: "answer" },
          el("button", { type: "button", className: "tick" + (i === q.correct ? " on" : ""), textContent: "✓", title: "The right answer", onclick: () => { q.correct = i; redraw(); } }),
          picButton(a.picture, "", () => pickPicture(a.text || `answer ${i + 1}`, (p) => { a.picture = p; redraw(); }, a.picture), "+ pic"),
          input,
          el("button", { type: "button", className: "ghost small", textContent: "✕", title: "Remove this answer", disabled: q.answers.length <= 2, onclick: () => {
            q.answers.splice(i, 1);
            if (q.correct >= q.answers.length || q.correct === i) q.correct = 0;
            else if (q.correct > i) q.correct--;
            redraw();
          } }));
      }),
      q.answers.length < 4
        ? el("button", { type: "button", className: "ghost small", textContent: "+ Add an answer", onclick: () => { q.answers.push({ text: "", picture: "" }); redraw(); } })
        : "");
  };

  draw();
}

/** A square button showing [picture], or [emptyLabel] when there isn't one. */
function picButton(picture, size, onclick, emptyLabel = "") {
  const b = el("button", { type: "button", className: "picbtn" + (size ? " " + size : "") + (picture ? "" : " empty"), onclick, title: "Choose a picture" });
  if (picture) b.append(pic(picture, "pic"));
  else b.textContent = emptyLabel;
  if (picture) b.firstChild.style.cssText = "width:100%;height:100%;font-size:inherit";
  return b;
}

// --- Picture picker: clipart, an uploaded picture, or an emoji --------------------

let clipart = null; // the catalogue, loaded the first time the picker opens
const GROUP_NAMES = ["Animals", "Food", "Places", "Activities", "Things", "Faces", "People", "Symbols", "Flags"];

async function loadClipart() {
  const get = (url) => fetch(url).then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); });
  // Country flags are drawn by tools/make_flags.js and live on the server itself.
  if (!clipart) clipart = Promise.all([get("/clipart/fluent.json"), get("/clipart/flags.json").catch(() => ({ emoji: [] }))])
    .then(([fluent, flags]) => ({ ...fluent, emoji: fluent.emoji.concat(flags.emoji) }))
    .catch((e) => { clipart = null; throw e; });
  return clipart;
}

/** Where a catalogue picture is: on the server ("/clipart/...") or on the Fluent Emoji CDN. */
function clipUrl(catalogue, path) {
  return path.startsWith("/") ? path : catalogue.base + path.split("/").map(encodeURIComponent).join("/");
}

/**
 * Lets the parent choose a picture for [what] (a word or question, used to search the
 * clipart first). [onPick] gets the picture: an emoji, a quiz image's path, or "" for none.
 */
function pickPicture(what, onPick, current) {
  const host = $("picker") || document.body.appendChild(el("div", { id: "picker" }));
  const close = () => { host.hidden = true; host.replaceChildren(); };
  const status = el("div", { className: "muted" });
  const grid = el("div", { className: "clip-grid" });
  const search = el("input", { type: "text", placeholder: "Search pictures, e.g. cow", autocomplete: "off", enterkeyhint: "search" });
  const words = String(what || "").toLowerCase().replace(/^(question|answer) \d+$|the quiz/, "").match(/[a-z]{3,}/g) || [];
  search.value = words.filter((w) => !["which", "what", "who", "the", "does", "say", "says", "one", "how", "many"].includes(w)).slice(-1)[0] || "";
  let group = 0;
  let catalogue = null;
  const file = el("input", { type: "file", accept: "image/*", hidden: true });

  const done = (picture) => { close(); onPick(picture); };
  const busy = (text) => { status.textContent = text; };

  const show = () => {
    if (!catalogue) return;
    const q = search.value.trim().toLowerCase();
    const terms = q.split(/\s+/).filter(Boolean);
    const found = catalogue.emoji.filter(([name, , , keywords, g]) => terms.length
      ? terms.every((t) => (name + " " + keywords).toLowerCase().includes(t))
      : g === group);
    // Whole-word name matches first: "cow" before "cowboy hat face".
    if (terms.length) found.sort((a, b) => (b[0].toLowerCase() === q) - (a[0].toLowerCase() === q) || a[0].length - b[0].length);
    groupsRow.hidden = terms.length > 0;
    grid.replaceChildren(...found.slice(0, 150).map(([name, , path]) => el("button", { type: "button", title: name, onclick: () => chooseClip(name, path) },
      el("img", { src: clipUrl(catalogue, path), alt: name, loading: "lazy" }))));
    status.textContent = found.length ? (found.length > 150 ? `First 150 of ${found.length}. Search to narrow it down.` : "") : "Nothing found. Try another word, or upload a picture.";
  };

  const groupsRow = el("div", { className: "chips" });
  const drawGroups = () => groupsRow.replaceChildren(...GROUP_NAMES.map((n, i) => el("button", { type: "button", className: i === group ? "on" : "", textContent: n, onclick: () => { group = i; drawGroups(); show(); } })));
  drawGroups();
  search.addEventListener("input", show);

  // Clipart is copied to the server, so the TV never needs the internet beyond it.
  async function chooseClip(name, path) {
    busy(`Adding ${name}…`);
    try {
      const source = path.startsWith("/clipart/flags/") ? "flag:" + path.slice(15, -4) : "fluent:" + path;
      // Already copied before? Then nothing needs sending.
      const known = await sendImage(null, source).catch(() => null);
      if (known && known.path) return done(known.path);
      const res = await fetch(clipUrl(catalogue, path));
      if (!res.ok) throw new Error("Couldn't get that picture. Try another.");
      const blob = await shrinkPicture(await res.blob(), 256);
      done((await sendImage(blob, source)).path);
    } catch (e) {
      if (e instanceof AuthError) { close(); return signedOut(); }
      busy(e.message);
    }
  }

  file.onchange = async () => {
    const f = file.files[0];
    file.value = "";
    if (!f) return;
    busy("Adding your picture…");
    try {
      done((await sendImage(await shrinkPicture(f, 512), "")).path);
    } catch (e) {
      if (e instanceof AuthError) { close(); return signedOut(); }
      busy(e.message || "Couldn't read that picture. Try a JPEG or PNG.");
    }
  };

  const emoji = el("input", { type: "text", placeholder: "or type an emoji", maxLength: 16, style: "max-width:160px" });
  host.replaceChildren(el("div", { className: "scrim", onclick: close }), el("div", { className: "sheet stack" },
    el("div", { className: "row" },
      el("div", { className: "grow", style: "font-weight:800;font-size:20px", textContent: "Choose a picture" }),
      current ? pic(current, "picbtn") : ""),
    search,
    groupsRow,
    status,
    grid,
    el("div", { className: "row" },
      el("button", { type: "button", className: "grow", textContent: "📷 Upload a picture", onclick: () => file.click() }), file),
    el("div", { className: "row" }, emoji,
      el("button", { type: "button", className: "ghost", textContent: "Use emoji", onclick: () => emoji.value.trim() && done(emoji.value.trim()) })),
    el("div", { className: "muted", textContent: "Clipart: Fluent Emoji by Microsoft and flag-icons by Panayiotis Lipiridis (both MIT licence). Emoji typed in may not show on older Fire TV sticks; clipart and uploads always do." }),
    ...(current ? [el("button", { type: "button", className: "ghost", textContent: "No picture", onclick: () => done("") })] : []),
    el("button", { type: "button", className: "ghost", textContent: "Cancel", onclick: close })));
  host.hidden = false;

  busy("Loading pictures…");
  loadClipart().then((c) => { catalogue = c; show(); }).catch(() => busy("Couldn't load the clipart. You can still upload a picture."));
}

/** Uploads a quiz picture ([blob] null to ask only whether [source] is already there). */
async function sendImage(blob, source) {
  const form = new FormData();
  if (blob) form.append("image", blob, blob.type === "image/png" ? "picture.png" : blob.type === "image/webp" ? "picture.webp" : "picture.jpg");
  if (source) form.append("source", source);
  const res = await fetch("/api/wonder/quiz-images/add", { method: "POST", headers: { Authorization: token }, body: form });
  const data = await res.json().catch(() => ({}));
  if (res.status === 401) throw new AuthError(data.message || "Please sign in again.");
  if (!res.ok) throw new Error(data.message || `Something went wrong (${res.status}).`);
  return data;
}

/**
 * A picture no bigger than [edge] on its longest side, keeping transparency (WebP where the
 * browser can make it, PNG otherwise), so clipart sits nicely on the TV's coloured buttons.
 */
async function shrinkPicture(file, edge) {
  const src = URL.createObjectURL(file);
  try {
    const img = new Image();
    img.src = src;
    await img.decode();
    const scale = Math.min(1, edge / Math.max(img.naturalWidth, img.naturalHeight));
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(img.naturalWidth * scale));
    canvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
    const ctx = canvas.getContext("2d");
    ctx.imageSmoothingQuality = "high";
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    const blob = (type, quality) => new Promise((resolve) => canvas.toBlob(resolve, type, quality));
    const webp = await blob("image/webp", 0.85);
    if (webp && webp.type === "image/webp") return webp;
    return (await blob("image/png")) || Promise.reject(new Error("Couldn't read that picture."));
  } finally {
    URL.revokeObjectURL(src);
  }
}
