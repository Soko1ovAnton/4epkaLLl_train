// Simple robust downloader: node dl.js <url> <outPath> [retries]
const https = require('https');
const fs = require('fs');
const path = require('path');

function get(url, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (redirects > 5) return reject(new Error('too many redirects'));
    const q = https.request(url, { headers: { 'User-Agent': 'dl/1.0' } }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        q.end();
        const loc = new URL(res.headers.location, url).toString();
        return resolve(get(loc, redirects + 1));
      }
      if (res.statusCode !== 200) {
        res.resume(); q.end();
        return reject(new Error('HTTP ' + res.statusCode + ' for ' + url));
      }
      resolve(res);
    });
    q.on('error', reject);
    q.end();
  });
}

async function main() {
  const [url, out, retries = '3'] = process.argv.slice(2);
  fs.mkdirSync(path.dirname(path.resolve(out)), { recursive: true });
  for (let i = 0; i <= Number(retries); i++) {
    try {
      const res = await get(url);
      const total = Number(res.headers['content-length'] || 0);
      let done = 0, lastPct = -1;
      await new Promise((resolve, reject) => {
        const ws = fs.createWriteStream(out);
        res.on('data', (c) => {
          done += c.length;
          const pct = total ? Math.floor((done / total) * 100) : 0;
          if (pct >= lastPct + 5) { lastPct = pct; console.log(out, pct + '%', done + '/' + (total || '?')); }
        });
        res.pipe(ws);
        ws.on('error', reject);
        res.on('error', reject);
        ws.on('finish', resolve);
      });
      const size = fs.statSync(out).size;
      if (total && size !== total) throw new Error('size mismatch: ' + size + ' != ' + total);
      console.log('DONE', out, size, 'bytes');
      process.exit(0);
    } catch (e) {
      console.error('attempt', i + 1, 'failed:', e.message);
      if (i === Number(retries)) { process.exit(1); }
      await new Promise((r) => setTimeout(r, 3000));
    }
  }
}
main();