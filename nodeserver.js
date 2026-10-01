const http = require('http');
const fs = require('fs');
const path = require('path');
const port = 8080;
const dir = process.argv[2] || '.';
const mime = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.gif': 'image/gif',
  '.ico': 'image/x-icon',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2',
  '.woff': 'font/woff'
};

const sseClients = new Set();

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const urlPath = req.url.split('?')[0];

  // SSE Real-Time Event Stream for Instant Display Updates
  if (urlPath === '/api/events') {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
      'Access-Control-Allow-Origin': '*'
    });
    res.write('retry: 1500\n\n');
    sseClients.add(res);
    req.on('close', () => sseClients.delete(res));
    return;
  }

  // Real-time broadcast endpoint for instant display commands (Salat mode, test countdown, etc.)
  if (urlPath === '/api/broadcast' && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
      try {
        const parsed = JSON.parse(body);
        const msg = `data: ${JSON.stringify(parsed)}\n\n`;
        sseClients.forEach(client => {
          try { client.write(msg); } catch(e) { sseClients.delete(client); }
        });
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true }));
      } catch(e) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Invalid JSON' }));
      }
    });
    return;
  }

  // API endpoint for persistent settings
  if (urlPath === '/api/settings') {
    const settingsFile = path.join(dir, 'settings.json');
    if (req.method === 'GET') {
      fs.readFile(settingsFile, 'utf8', (err, data) => {
        if (err) {
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end('{}');
        } else {
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(data);
        }
      });
      return;
    }
    if (req.method === 'POST') {
      let body = '';
      req.on('data', chunk => body += chunk);
      req.on('end', () => {
        try {
          const parsed = JSON.parse(body);

          let existing = {};
          try {
            if (fs.existsSync(settingsFile)) {
              existing = JSON.parse(fs.readFileSync(settingsFile, 'utf8'));
            }
          } catch(e) {}

          const merged = Object.assign({}, existing, parsed);

          merged.lat = 21.8355;
          merged.lng = 92.0780;
          merged.location = 'চকরিয়া, কক্সবাজার';
          merged.updatedAt = Date.now();

          const jsonStr = JSON.stringify(merged, null, 2);

          // Mirror to public, desktop and android-tv assets
          try { fs.writeFileSync(path.join(dir, 'public', 'settings.json'), jsonStr, 'utf8'); } catch(e) {}
          try {
            const deskPath = 'C:\\Users\\QC\\Desktop\\mosque\\settings.json';
            if (fs.existsSync(path.dirname(deskPath))) fs.writeFileSync(deskPath, jsonStr, 'utf8');
          } catch(e) {}
          try {
            const assetPath = path.join(dir, 'android-tv', 'app', 'src', 'main', 'assets', 'settings.json');
            if (fs.existsSync(path.dirname(assetPath))) fs.writeFileSync(assetPath, jsonStr, 'utf8');
          } catch(e) {}

          fs.writeFile(settingsFile, jsonStr, 'utf8', (err) => {
            if (err) {
              res.writeHead(500, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({ error: err.message }));
            } else {
              res.writeHead(200, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({ success: true, settings: merged }));

              // Real-time broadcast to all connected displays
              const msg = `data: ${JSON.stringify({ type: 'UPDATE_SETTINGS', settings: merged })}\n\n`;
              sseClients.forEach(client => {
                try { client.write(msg); } catch(e) { sseClients.delete(client); }
              });
            }
          });
        } catch (e) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'Invalid JSON' }));
        }
      });
      return;
    }
  }

  // Static files
  let fp = path.join(dir, urlPath === '/' ? '/display.html' : urlPath);
  fs.readFile(fp, (e, d) => {
    if (e) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('Not Found');
      return;
    }
    const ct = mime[path.extname(fp)] || 'text/plain';
    const headers = { 'Content-Type': ct };
    // Required header for Service Worker to control full scope
    if (urlPath === '/sw.js') {
      headers['Service-Worker-Allowed'] = '/';
      headers['Cache-Control'] = 'no-cache';
    }
    // Manifest must not be cached aggressively
    if (urlPath === '/manifest.json') {
      headers['Cache-Control'] = 'no-cache';
    }
    res.writeHead(200, headers);
    res.end(d);
  });
});

let currentPort = parseInt(process.env.PORT, 10) || 8080;

function startServer(p) {
  const s = server.listen(p, () => {
    console.log('Server running on http://localhost:' + p);
  });
  s.once('error', (err) => {
    if ((err.code === 'EACCES' || err.code === 'EADDRINUSE') && p === 8080) {
      console.log(`Port 8080 is unavailable (${err.code}), switching to port 8081...`);
      startServer(8081);
    } else {
      console.error('Server error:', err);
    }
  });
}

startServer(currentPort);
