const CACHE_NAME = "black-walnut-shell-__BUILD_VERSION__";
const APP_SHELL = ["/", "/index.html", "/manifest.webmanifest", "/icons/icon-192.png", "/icons/icon-512.png", "/icons/icon-maskable-512.png"];

self.addEventListener("install", (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE_NAME);
    await cache.addAll(APP_SHELL);
    const response = await fetch("/index.html", { cache: "no-store" });
    const html = await response.clone().text();
    await cache.put("/index.html", response);
    const assets = [...html.matchAll(/(?:src|href)="(\/assets\/[^"]+)"/g)].map((match) => match[1]);
    const uniqueAssets = [...new Set(assets)];
    if (uniqueAssets.length > 0) await cache.addAll(uniqueAssets);
    const stylesheets = uniqueAssets.filter((asset) => asset.endsWith(".css"));
    const fontAssets = [];
    for (const stylesheet of stylesheets) {
      const css = await (await fetch(stylesheet, { cache: "no-store" })).text();
      fontAssets.push(...[...css.matchAll(/url\(["']?(\/assets\/[^)"']+)["']?\)/g)].map((match) => match[1]));
    }
    if (fontAssets.length > 0) await cache.addAll([...new Set(fontAssets)]);
  })());
});

self.addEventListener("activate", (event) => {
  event.waitUntil(caches.keys().then((names) => Promise.all(names.filter((name) => name !== CACHE_NAME).map((name) => caches.delete(name)))));
});

self.addEventListener("message", (event) => {
  if (event.data?.type === "SKIP_WAITING") self.skipWaiting();
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin) return;

  if (event.request.mode === "navigate") {
    event.respondWith(fetch(event.request).then((response) => {
      const copy = response.clone();
      void caches.open(CACHE_NAME).then((cache) => cache.put("/index.html", copy));
      return response;
    }).catch(() => caches.match("/index.html")));
    return;
  }

  event.respondWith(caches.match(event.request).then((cached) => {
    const network = fetch(event.request).then((response) => {
      if (response.ok) void caches.open(CACHE_NAME).then((cache) => cache.put(event.request, response.clone()));
      return response;
    });
    return cached ?? network;
  }));
});
