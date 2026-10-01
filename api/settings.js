const fs = require('fs');
const path = require('path');

let memorySettings = null;
const tmpPath = path.join('/tmp', 'settings.json');

module.exports = (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    return res.end();
  }

  // Helper to load base fallback settings
  function getFallbackSettings() {
    try {
      const p = path.join(process.cwd(), 'settings.json');
      if (fs.existsSync(p)) {
        return JSON.parse(fs.readFileSync(p, 'utf8'));
      }
    } catch(e) {}
    try {
      const p = path.join(process.cwd(), 'public', 'settings.json');
      if (fs.existsSync(p)) {
        return JSON.parse(fs.readFileSync(p, 'utf8'));
      }
    } catch(e) {}
    return {};
  }

  if (req.method === 'POST') {
    let raw = req.body;
    let parsed = {};
    if (typeof raw === 'string') {
      try { parsed = JSON.parse(raw); } catch(e) {}
    } else if (raw && typeof raw === 'object') {
      parsed = raw;
    }

    let existing = memorySettings;
    if (!existing) {
      try {
        if (fs.existsSync(tmpPath)) {
          existing = JSON.parse(fs.readFileSync(tmpPath, 'utf8'));
        }
      } catch(e) {}
    }
    if (!existing) {
      existing = getFallbackSettings();
    }

    const merged = Object.assign({}, existing, parsed);
    merged.lat = 21.8355;
    merged.lng = 92.0780;
    merged.location = 'চকরিয়া, কক্সবাজার';
    merged.updatedAt = Date.now();

    memorySettings = merged;
    try {
      fs.writeFileSync(tmpPath, JSON.stringify(merged, null, 2), 'utf8');
    } catch(e) {}

    res.statusCode = 200;
    res.setHeader('Content-Type', 'application/json');
    return res.end(JSON.stringify({ success: true, settings: merged }));
  }

  if (req.method === 'GET') {
    if (memorySettings) {
      res.statusCode = 200;
      res.setHeader('Content-Type', 'application/json');
      return res.end(JSON.stringify(memorySettings));
    }
    try {
      if (fs.existsSync(tmpPath)) {
        const d = JSON.parse(fs.readFileSync(tmpPath, 'utf8'));
        memorySettings = d;
        res.statusCode = 200;
        res.setHeader('Content-Type', 'application/json');
        return res.end(JSON.stringify(d));
      }
    } catch(e) {}

    const fb = getFallbackSettings();
    res.statusCode = 200;
    res.setHeader('Content-Type', 'application/json');
    return res.end(JSON.stringify(fb));
  }

  res.statusCode = 405;
  res.setHeader('Content-Type', 'application/json');
  return res.end(JSON.stringify({ error: 'Method Not Allowed' }));
};
