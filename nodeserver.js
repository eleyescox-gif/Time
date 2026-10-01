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

  // API endpoint for persistent settings (Multi-Tenant)
  if (urlPath === '/api/settings' || urlPath === '/api/mosques') {
    const parsedUrl = new URL(req.url, 'http://localhost');
    const action = parsedUrl.searchParams.get('action');

    // List all mosques
    if (urlPath === '/api/mosques' || action === 'list') {
      let list = [{ id: 'default', nameBn: 'পূর্ব মোহাজের পাড়া জামে মসজিদ', location: 'চকরিয়া, কক্সবাজার' }];
      try {
        const regFile = path.join(dir, 'mosque_registry.json');
        if (fs.existsSync(regFile)) list = JSON.parse(fs.readFileSync(regFile, 'utf8'));
      } catch(e) {}
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ success: true, mosques: list }));
      return;
    }

    let rawId = parsedUrl.searchParams.get('m') || parsedUrl.searchParams.get('id');
    const mId = (rawId && rawId !== 'default' && rawId !== 'null') 
      ? String(rawId).toLowerCase().trim().replace(/[^a-z0-9_-]/g, '-').slice(0, 50) 
      : 'default';

    const settingsFile = (mId === 'default') 
      ? path.join(dir, 'settings.json') 
      : path.join(dir, `settings_${mId}.json`);

    if (req.method === 'GET') {
      fs.readFile(settingsFile, 'utf8', (err, data) => {
        if (err) {
          // If custom mosque file doesn't exist, generate template from default
          let fallback = {};
          try {
            fallback = JSON.parse(fs.readFileSync(path.join(dir, 'settings.json'), 'utf8'));
          } catch(e) {}
          if (mId !== 'default') {
            fallback.mosqueId = mId;
            fallback.nameBn = 'নতুন জামে মসজিদ';
            fallback.nameEn = 'New Jame Masjid';
            fallback.location = 'ঢাকা, বাংলাদেশ';
            fallback.lat = 23.8103;
            fallback.lng = 90.4125;
            fallback.updatedAt = 0;
          }
          res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
          res.end(JSON.stringify(fallback));
        } else {
          res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
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
            } else if (mId === 'default' && fs.existsSync(path.join(dir, 'settings.json'))) {
              existing = JSON.parse(fs.readFileSync(path.join(dir, 'settings.json'), 'utf8'));
            }
          } catch(e) {}

          // PIN validation
          if (existing.pin && existing.pin.trim() !== '') {
            const clientPin = String(parsed.pin || parsed.adminPin || '').trim();
            if (clientPin !== String(existing.pin).trim()) {
              res.writeHead(401, { 'Content-Type': 'application/json; charset=utf-8' });
              res.end(JSON.stringify({ error: 'ভুল এডমিন পিন কোড!', invalidPin: true }));
              return;
            }
          }

          const merged = Object.assign({}, existing, parsed);
          merged.mosqueId = mId;
          merged.updatedAt = Date.now();

          if (mId === 'default') {
            if (!merged.lat) merged.lat = 21.8355;
            if (!merged.lng) merged.lng = 92.0780;
            if (!merged.location) merged.location = 'চকরিয়া, কক্সবাজার';
          } else {
            if (!merged.lat) merged.lat = 23.8103;
            if (!merged.lng) merged.lng = 90.4125;
            if (!merged.location) merged.location = 'ঢাকা, বাংলাদেশ';
          }

          const jsonStr = JSON.stringify(merged, null, 2);

          // Write settings for this mosque
          fs.writeFile(settingsFile, jsonStr, 'utf8', (err) => {
            if (err) {
              res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' });
              res.end(JSON.stringify({ error: err.message }));
              return;
            }

            // Mirror if default mosque
            if (mId === 'default') {
              try { fs.writeFileSync(path.join(dir, 'public', 'settings.json'), jsonStr, 'utf8'); } catch(e) {}
              try {
                const deskPath = 'C:\\Users\\QC\\Desktop\\mosque\\settings.json';
                if (fs.existsSync(path.dirname(deskPath))) fs.writeFileSync(deskPath, jsonStr, 'utf8');
              } catch(e) {}
              try {
                const assetPath = path.join(dir, 'android-tv', 'app', 'src', 'main', 'assets', 'settings.json');
                if (fs.existsSync(path.dirname(assetPath))) fs.writeFileSync(assetPath, jsonStr, 'utf8');
              } catch(e) {}
            }

            // Update Registry
            try {
              const regFile = path.join(dir, 'mosque_registry.json');
              let list = [{ id: 'default', nameBn: 'পূর্ব মোহাজের পাড়া জামে মসজিদ', location: 'চকরিয়া, কক্সবাজার' }];
              if (fs.existsSync(regFile)) list = JSON.parse(fs.readFileSync(regFile, 'utf8'));
              const idx = list.findIndex(item => item.id === mId);
              const entry = { id: mId, nameBn: merged.nameBn || 'জামে মসজিদ', location: merged.location || 'বাংলাদেশ', updatedAt: merged.updatedAt };
              if (idx >= 0) list[idx] = entry; else list.push(entry);
              fs.writeFileSync(regFile, JSON.stringify(list, null, 2), 'utf8');
              try { fs.writeFileSync(path.join(dir, 'public', 'mosque_registry.json'), JSON.stringify(list, null, 2), 'utf8'); } catch(e) {}
            } catch(e) {}

            res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify({ success: true, mosqueId: mId, settings: merged }));

            // Real-time broadcast
            const msg = `data: ${JSON.stringify({ type: 'UPDATE_SETTINGS', mosqueId: mId, settings: merged })}\n\n`;
            sseClients.forEach(client => {
              try { client.write(msg); } catch(e) { sseClients.delete(client); }
            });
          });
        } catch (e) {
          res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
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
