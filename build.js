const fs = require('fs');
const path = require('path');

const pub = path.join(__dirname, 'public');
if (!fs.existsSync(pub)) {
  fs.mkdirSync(pub, { recursive: true });
}

const files = [
  'index.html',
  'display.html',
  'admin.html',
  'portal.html',
  'settings.json',
  'install.html',
  'logo.png',
  'bg_display.jpg',
  'MyMasjidTV_INSTALL.apk',
  'manifest.json',
  'sw.js',
  'qrcode.min.js',
  'vercel.json',
  'coxs-bazar-times.js',
  'islamic-slides.js',
  'circle_frame1.png',
  'circle_frame2.png',
  'mosque_registry.json'
];

files.forEach(file => {
  const src = path.join(__dirname, file);
  const dest = path.join(pub, file);
  if (fs.existsSync(src)) {
    fs.copyFileSync(src, dest);
  }
});

// Copy fonts directory
const fontsSrc = path.join(__dirname, 'fonts');
const fontsDest = path.join(pub, 'fonts');
if (fs.existsSync(fontsSrc)) {
  if (!fs.existsSync(fontsDest)) fs.mkdirSync(fontsDest, { recursive: true });
  fs.readdirSync(fontsSrc).forEach(f => {
    fs.copyFileSync(path.join(fontsSrc, f), path.join(fontsDest, f));
  });
}

console.log('Public build folder prepared successfully with all assets for Vercel.');
