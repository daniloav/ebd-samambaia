/*
 * Service worker do EBD ICES (PWA).
 * Estratégia NETWORK-FIRST: online sempre busca a versão fresca (sem risco de
 * servir versão antiga após deploy); o cache só é usado como fallback offline.
 * Nunca cacheia chamadas de API.
 *
 * Auto-recuperação: o nome do cache é versionado (CACHE). Ao ATIVAR uma versão
 * nova do SW, todos os caches antigos são apagados e assumimos o controle das
 * abas na hora (skipWaiting + clients.claim), evitando "deadlock" de SW velho.
 * Se algum asset com hash sumiu após um deploy, quem lida com a recarga é o
 * script de auto-recuperação no index.html.
 */
const CACHE = 'ebd-shell-v2';

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
        // Só cacheia respostas OK (evita gravar 404/redirect como se fossem o app).
        if (res && res.ok && res.type === 'basic') {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(req, copy)).catch(() => {});
        }
        return res;
      })
      .catch(() =>
        caches.match(req).then((r) => r || caches.match('/index.html'))
      )
  );
});
