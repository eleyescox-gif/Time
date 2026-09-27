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
  'settings.json',
  'logo.png',
  'bg_display.jpg',
  'MyMasjidTV_INSTALL.apk',
  'manifest.json',
  'sw.js',
  'vercel.json'
];

files.forEach(file => {
  const src = path.join(__dirname, file);
  const dest = path.join(pub, file);
  if (fs.existsSync(src)) {
    fs.copyFileSync(src, dest);
  }
});

console.log('Public build folder prepared successfully with all assets for Vercel.');
