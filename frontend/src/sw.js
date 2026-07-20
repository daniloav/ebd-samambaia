/*
 * Service worker do EBD Adultos (PWA).
 * Estratégia NETWORK-FIRST: online sempre busca a versão fresca (sem risco de
 * servir versão antiga após deploy); o cache só é usado como fallback offline.
 * Nunca cacheia chamadas de API.
 */
const CACHE = 'ebd-shell-v1';

self.addEventListener('install', () => self.skipWaiting());

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return; // externos passam direto
  if (url.pathname.startsWith('/api/')) return;     // API nunca é cacheada

  event.respondWith(
    fetch(req)
      .then((res) => {
        const copy = res.clone();
        caches.open(CACHE).then((c) => c.put(req, copy)).catch(() => {});
        return res;
      })
      .catch(() =>
        caches.match(req).then((r) => r || caches.match('/index.html'))
      )
  );
});
