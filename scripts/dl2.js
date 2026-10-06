// Chunked resumable downloader: node dl2.js <url> <outPath>
// Downloads in 8MB range chunks with per-chunk retries; resumes partial files.
const https = require('https');
const http = require('http');
const fs = require('fs');
const path = require('path');

const CHUNK = 8 * 1024 * 1024;

function fetchWithRedirects(url, headers, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (redirects > 5) return reject(new Error('too many redirects'));
    const mod = url.startsWith('https') ? https : http;
    const q = mod.request(url, { headers }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume(); q.end();
        return resolve(fetchWithRedirects(new URL(res.headers.location, url).toString(), headers, redirects + 1));
      }
      resolve(res); // caller checks status
    });
    q.on('error', reject);
    q.setTimeout(60000, () => q.destroy(new Error('timeout')));
    q.end();
  });
}

function fetchRange(url, start, end, attempt = 0) {
  return fetchWithRedirects(url, {
    'User-Agent': 'dl2/1.0',
    Range: `bytes=${start}-${end}`,
    'Accept-Encoding': 'identity',
  }).then(async (res) => {
    if (res.statusCode === 206) return res;
    if (res.statusCode === 200 && start === 0) return res; // full body, no range support
    res.resume();
    if (attempt < 6) {
      await new Promise((r) => setTimeout(r, 2000 * (attempt + 1)));
      return fetchRange(url, start, end, attempt + 1);
    }
    throw new Error('range request failed: HTTP ' + res.statusCode);
  });
}

async function main() {
  const [url, out] = process.argv.slice(2);
  fs.mkdirSync(path.dirname(path.resolve(out)), { recursive: true });

  // Probe total size
  const head = await fetchWithRedirects(url, { 'User-Agent': 'dl2/1.0', 'Accept-Encoding': 'identity' });
  if (head.statusCode !== 200) throw new Error('probe failed: HTTP ' + head.statusCode);
  const total = Number(head.headers['content-length'] || 0);
  head.resume();
  if (!total) throw new Error('no content-length; cannot chunk-download');
  console.log(`total: ${total} bytes (${(total / 1048576).toFixed(1)} MB)`);

  let offset = fs.statSync(out).size;
  if (offset > total) { fs.truncateSync(out, 0); offset = 0; }
  if (offset === total) { console.log('already complete'); return; }

  const t0 = Date.now();
  while (offset < total) {
    const end = Math.min(offset + CHUNK, total) - 1;
    try {
      const res = await fetchRange(url, offset, end);
      const fd = fs.openSync(out, 'r+');
      try {
        await new Promise((resolve, reject) => {
          const ws = fs.createWriteStream(out, { flags: 'r+', start: offset, fd, autoClose: false });
          res.pipe(ws);
          ws.on('error', reject);
          ws.on('finish', resolve);
          res.on('error', reject);
        });
      } finally {
        try { fs.closeSync(fd); } catch (_) { /* already closed */ }
      }
      // derive actual progress from file size (sequential writes)
      offset = fs.statSync(out).size;
      const pct = ((offset / total) * 100).toFixed(1);
      const secs = ((Date.now() - t0) / 1000).toFixed(0);
      console.log(`${out}: ${pct}% (${offset}/${total}) ${secs}s`);
    } catch (e) {
      const cur = fs.statSync(out).size;
      offset = cur;
      console.error(`chunk at ${cur} failed: ${e.message}; resuming...`);
      await new Promise((r) => setTimeout(r, 2000));
    }
  }
  const size = fs.statSync(out).size;
  if (size !== total) throw new Error(`final size mismatch: ${size} != ${total}`);
  console.log('DONE', out, size, 'bytes');
}
main().catch((e) => { console.error('FATAL', e.message); process.exit(1); });