// The Family tab's TV menu pictures: the pictures Gemini drew for the TV's home page and
// Games and Quizzes tiles (server/pb_hooks/menu_lib.js), with a tap to have one drawn again.
// Loaded after the main script in index.html and uses its helpers ($, el, api, photoUrl,
// showError, toast).

document.head.append(el("style", { textContent: `
  .menu-pics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
  .menu-pic { background: rgba(0,0,0,.18); border-radius: 10px; padding: 6px; text-align: center; cursor: pointer; font-size: 13px; font-weight: 700; }
  .menu-pic .frame { aspect-ratio: 1; border-radius: 8px; overflow: hidden; display: grid; place-items: center; background: rgba(255,255,255,.08); color: var(--muted); font-weight: 600; font-size: 12px; margin-bottom: 4px; }
  .menu-pic.home .frame { aspect-ratio: 4 / 3; }
  .menu-pic img { width: 100%; height: 100%; object-fit: cover; display: block; }
`}));

let menuPoll = null;

async function loadMenuPictures() {
  if (!$("menuPictures")) return;
  try {
    renderMenuPictures(await api("/api/wonder/menu-pictures"));
  } catch (e) {
    showError(e);
  }
}

function renderMenuPictures(data) {
  const pictures = data.pictures || [];
  if (!data.drawing) {
    $("menuPictures").replaceChildren(el("div", { className: "muted", textContent:
      "The TV's menu tiles show emoji for now. With a Gemini key on the server (GEMINI_API_KEY in the install task, see the server README), Gemini draws a picture for each one." }));
    return;
  }
  const tile = (p) => {
    const frame = el("div", { className: "frame" });
    if (p.picture) {
      const img = el("img", { alt: "" });
      photoUrl(p.picture).then((url) => { img.src = url; }).catch(() => {});
      frame.append(img);
    } else {
      frame.textContent = p.state === "failed" ? "Tap to try again" : "Drawing…";
    }
    const waiting = p.state === "" || p.state === "drawing";
    return el("div", { className: `menu-pic${p.home ? " home" : ""}`, onclick: () => redrawMenuPicture(p) },
      frame, el("div", { textContent: waiting && p.picture ? `${p.title} (redrawing…)` : p.title }));
  };
  const home = pictures.filter((p) => p.home);
  const games = pictures.filter((p) => !p.home);
  $("menuPictures").replaceChildren(
    el("div", { className: "muted", textContent: "Drawn by Gemini for the TV. Tap one to have it drawn again." }),
    el("div", { className: "stat-h", textContent: "HOME PAGE" }),
    el("div", { className: "menu-pics" }, ...home.map(tile)),
    el("div", { className: "stat-h", textContent: "GAMES AND QUIZZES" }),
    el("div", { className: "menu-pics" }, ...games.map(tile)));

  // While any are being drawn, look again now and then.
  clearTimeout(menuPoll);
  if (pictures.some((p) => p.state === "" || p.state === "drawing")) {
    menuPoll = setTimeout(() => { if (tab === "family") loadMenuPictures(); }, 20000);
  }
}

async function redrawMenuPicture(p) {
  if (!confirm(`Draw a new picture for ${p.title}? The TV keeps the old one until it's ready.`)) return;
  try {
    renderMenuPictures(await api("/api/wonder/menu-pictures/redraw", { key: p.key }));
    toast("Drawing a new one. It takes a minute or two.");
  } catch (e) {
    showError(e);
  }
}
