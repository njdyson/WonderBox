// Service worker: makes the app installable. The page and its scripts are fetched fresh
// when online (so updates show straight away; iPhones otherwise keep using an old script
// next to a new page) and fall back to the cached copy offline. API calls and the
// PocketBase dashboard are never cached.
const CACHE = "wonderbox-v2";
const SHELL = ["/", "/manifest.webmanifest", "/icon-192.png", "/icon-512.png", "/apple-touch-icon.png"];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== "GET" || url.origin !== location.origin) return;
  if (url.pathname.startsWith("/api/") || url.pathname.startsWith("/_/")) return;
  if (event.request.mode === "navigate") {
    event.respondWith(
      fetch(event.request)
        .then((res) => {
          const copy = res.clone();
          caches.open(CACHE).then((cache) => cache.put("/", copy));
          return res;
        })
        .catch(() => caches.match("/")),
    );
    return;
  }
  // Scripts and styles: checked with the server every time, cached for offline.
  if (/\.(js|css|webmanifest)$/.test(url.pathname)) {
    event.respondWith(
      fetch(event.request, { cache: "no-cache" })
        .then((res) => {
          if (res.ok) { const copy = res.clone(); caches.open(CACHE).then((cache) => cache.put(event.request, copy)); }
          return res;
        })
        .catch(() => caches.match(event.request)),
    );
    return;
  }
  event.respondWith(caches.match(event.request).then((hit) => hit || fetch(event.request)));
});
