const fs = require('fs');
const path = require('path');

// Multi-tenant memory stores
const memoryStores = new Map();
const registryPath = path.join('/tmp', 'mosque_registry.json');

// Helper to sanitize mosque identifier
function sanitizeId(id) {
  if (!id || id === 'default' || id === 'null' || id === 'undefined') return 'default';
  return String(id).toLowerCase().trim().replace(/[^a-z0-9_-]/g, '-').replace(/-+/g, '-').slice(0, 50) || 'default';
}

function getMosqueFilePath(mId) {
  return path.join('/tmp', `settings_${mId}.json`);
}

// Read base fallback template
function getFallbackSettings(mId) {
  let base = {};
  try {
    const p = path.join(process.cwd(), 'settings.json');
    if (fs.existsSync(p)) base = JSON.parse(fs.readFileSync(p, 'utf8'));
  } catch(e) {}
  if (!base.nameBn) {
    try {
      const p = path.join(process.cwd(), 'public', 'settings.json');
      if (fs.existsSync(p)) base = JSON.parse(fs.readFileSync(p, 'utf8'));
    } catch(e) {}
  }

  if (mId && mId !== 'default') {
    // Generate clean template for new mosque
    return Object.assign({}, base, {
      mosqueId: mId,
      nameBn: 'নতুন জামে মসজিদ',
      nameEn: 'New Jame Masjid',
      nameAr: 'مسجد النور',
      location: 'ঢাকা, বাংলাদেশ',
      lat: 23.8103,
      lng: 90.4125,
      fajrJm: '05:30',
      dhuhrJm: '13:30',
      asrJm: '16:45',
      maghribJm: 'auto',
      ishaJm: '20:30',
      jummahJamaat: '13:30',
      ticker: 'নামাজের সময় মোবাইল ফোন বন্ধ রাখুন। কাতারে সোজা ও ফাঁকা জায়গা পূরণ করে দাঁড়ান।',
      updatedAt: 0
    });
  }

  base.mosqueId = 'default';
  return base;
}

// Registry helpers to list mosques
function getRegistry() {
  try {
    if (fs.existsSync(registryPath)) {
      return JSON.parse(fs.readFileSync(registryPath, 'utf8'));
    }
  } catch(e) {}
  return [
    {
      id: 'default',
      nameBn: 'পূর্ব মোহাজের পাড়া জামে মসজিদ',
      location: 'চকরিয়া, কক্সবাজার',
      updatedAt: Date.now()
    }
  ];
}

function updateRegistry(mId, settings) {
  try {
    const list = getRegistry();
    const idx = list.findIndex(item => item.id === mId);
    const entry = {
      id: mId,
      nameBn: settings.nameBn || (mId === 'default' ? 'পূর্ব মোহাজের পাড়া জামে মসজিদ' : 'জামে মসজিদ'),
      location: settings.location || 'বাংলাদেশ',
      updatedAt: settings.updatedAt || Date.now()
    };
    if (idx >= 0) {
      list[idx] = entry;
    } else {
      list.push(entry);
    }
    fs.writeFileSync(registryPath, JSON.stringify(list, null, 2), 'utf8');
  } catch(e) {}
}

module.exports = (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    return res.end();
  }

  // Parse URL query parameters
  const urlObj = new URL(req.url, 'http://localhost');
  const action = urlObj.searchParams.get('action');

  // Directory / Registry API to list all mosques
  if (action === 'list') {
    res.statusCode = 200;
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    return res.end(JSON.stringify({ success: true, mosques: getRegistry() }));
  }

  // Determine Mosque ID
  let rawId = urlObj.searchParams.get('m') || urlObj.searchParams.get('id');
  if (!rawId && req.body) {
    try {
      const b = typeof req.body === 'string' ? JSON.parse(req.body) : req.body;
      rawId = b.mosqueId || b.m;
    } catch(e) {}
  }
  const mId = sanitizeId(rawId);
  const filePath = getMosqueFilePath(mId);

  // ══ POST: SAVE SETTINGS FOR SPECIFIC MOSQUE ══
  if (req.method === 'POST') {
    let raw = req.body;
    let parsed = {};
    if (typeof raw === 'string') {
      try { parsed = JSON.parse(raw); } catch(e) {}
    } else if (raw && typeof raw === 'object') {
      parsed = raw;
    }

    let existing = memoryStores.get(mId);
    if (!existing) {
      try {
        if (fs.existsSync(filePath)) {
          existing = JSON.parse(fs.readFileSync(filePath, 'utf8'));
        }
      } catch(e) {}
    }
    if (!existing) {
      existing = getFallbackSettings(mId);
    }

    // Security PIN Verification (if mosque has set an admin PIN)
    if (existing.pin && existing.pin.trim() !== '') {
      const clientPin = String(parsed.pin || parsed.adminPin || '').trim();
      if (clientPin !== String(existing.pin).trim()) {
        res.statusCode = 401;
        res.setHeader('Content-Type', 'application/json; charset=utf-8');
        return res.end(JSON.stringify({ error: 'ভুল এডমিন পিন কোড!', invalidPin: true }));
      }
    }

    const merged = Object.assign({}, existing, parsed);
    merged.mosqueId = mId;
    merged.updatedAt = Date.now();

    // Preserve GPS coordinates if given by user; default fallback if empty
    if (!merged.lat || isNaN(merged.lat)) {
      merged.lat = (mId === 'default') ? 21.8355 : 23.8103;
    }
    if (!merged.lng || isNaN(merged.lng)) {
      merged.lng = (mId === 'default') ? 92.0780 : 90.4125;
    }
    if (!merged.location) {
      merged.location = (mId === 'default') ? 'চকরিয়া, কক্সবাজার' : 'ঢাকা, বাংলাদেশ';
    }

    // Save to memory and disk
    memoryStores.set(mId, merged);
    try {
      fs.writeFileSync(filePath, JSON.stringify(merged, null, 2), 'utf8');
    } catch(e) {}

    // Update global directory registry
    updateRegistry(mId, merged);

    res.statusCode = 200;
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    return res.end(JSON.stringify({ success: true, mosqueId: mId, settings: merged }));
  }

  // ══ GET: RETRIEVE SETTINGS FOR SPECIFIC MOSQUE ══
  if (req.method === 'GET') {
    if (memoryStores.has(mId)) {
      res.statusCode = 200;
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      return res.end(JSON.stringify(memoryStores.get(mId)));
    }

    try {
      if (fs.existsSync(filePath)) {
        const d = JSON.parse(fs.readFileSync(filePath, 'utf8'));
        memoryStores.set(mId, d);
        res.statusCode = 200;
        res.setHeader('Content-Type', 'application/json; charset=utf-8');
        return res.end(JSON.stringify(d));
      }
    } catch(e) {}

    // Return fallback / initial template
    const fb = getFallbackSettings(mId);
    res.statusCode = 200;
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    return res.end(JSON.stringify(fb));
  }

  res.statusCode = 405;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  return res.end(JSON.stringify({ error: 'Method Not Allowed' }));
};
