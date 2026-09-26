// ═══════════════════════════════════════════════════════════════════
// MOSQUE DISPLAY — SERVICE WORKER (PWA Offline Support)
// Version: 1.0.0
// ═══════════════════════════════════════════════════════════════════

const CACHE_NAME = 'mosque-display-v1';
const OFFLINE_URL = '/display.html';

// Files to cache for offline use
const PRECACHE_URLS = [
  '/display.html',
  '/admin.html',
  '/logo.png',
  '/bg_display.jpg',
  '/manifest.json',
  '/settings.json'
];

// ── INSTALL: Pre-cache essential files ──
self.addEventListener('install', event => {
  console.log('[SW] Installing Mosque Display PWA...');
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => {
      console.log('[SW] Pre-caching files...');
      return cache.addAll(PRECACHE_URLS);
    }).then(() => self.skipWaiting())
  );
});

// ── ACTIVATE: Clean old caches ──
self.addEventListener('activate', event => {
  console.log('[SW] Activating Mosque Display PWA...');
  event.waitUntil(
    caches.keys().then(cacheNames => {
      return Promise.all(
        cacheNames
          .filter(name => name !== CACHE_NAME)
          .map(name => {
            console.log('[SW] Deleting old cache:', name);
            return caches.delete(name);
          })
      );
    }).then(() => self.clients.claim())
  );
});

// ── FETCH: Network-first, fallback to cache ──
self.addEventListener('fetch', event => {
  const { request } = event;
  const url = new URL(request.url);

  // Skip non-GET requests and cross-origin requests
  if (request.method !== 'GET' || url.origin !== location.origin) {
    return;
  }

  // API/settings requests: Network-first, short timeout
  if (url.pathname === '/settings.json' || url.pathname === '/api/') {
    event.respondWith(
      fetch(request)
        .then(response => {
          const clone = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(request, clone));
          return response;
        })
        .catch(() => caches.match(request))
    );
    return;
  }

  // All other assets: Cache-first, network fallback
  event.respondWith(
    caches.match(request).then(cached => {
      if (cached) {
        // Background update
        fetch(request).then(response => {
          caches.open(CACHE_NAME).then(cache => cache.put(request, response));
        }).catch(() => {});
        return cached;
      }

      // Not in cache — fetch from network and cache it
      return fetch(request).then(response => {
        if (!response || response.status !== 200 || response.type === 'opaque') {
          return response;
        }
        const clone = response.clone();
        caches.open(CACHE_NAME).then(cache => cache.put(request, clone));
        return response;
      }).catch(() => {
        // If both fail, return offline page
        if (request.destination === 'document') {
          return caches.match(OFFLINE_URL);
        }
      });
    })
  );
});

// ── BACKGROUND SYNC: Notify clients of updates ──
self.addEventListener('message', event => {
  if (event.data && event.data.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }
});
