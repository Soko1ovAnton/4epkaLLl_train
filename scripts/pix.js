// Usage: node pix.js <file.png> x1,y1 [x2,y2 ...]
// Minimal PNG decoder: 8-bit, colorType 2 (RGB) or 6 (RGBA), non-interlaced.
const fs = require('fs');
const zlib = require('zlib');

const file = process.argv[2];
const pts = process.argv.slice(3).map(s => s.split(',').map(Number));
const buf = fs.readFileSync(file);

let off = 8;
let ihdr = null, idat = [];
while (off < buf.length) {
    const len = buf.readUInt32BE(off);
    const type = buf.toString('ascii', off + 4, off + 8);
    const data = buf.subarray(off + 8, off + 8 + len);
    if (type === 'IHDR') ihdr = data;
    if (type === 'IDAT') idat.push(data);
    off += 12 + len;
    if (type === 'IEND') break;
}
const w = ihdr.readUInt32BE(0), h = ihdr.readUInt32BE(4);
const bitDepth = ihdr[8], colorType = ihdr[9], interlace = ihdr[12];
if (bitDepth !== 8) { console.error('bit depth ' + bitDepth + ' unsupported'); process.exit(1); }
if (interlace !== 0) { console.error('interlaced unsupported'); process.exit(1); }
const bpp = colorType === 6 ? 4 : colorType === 2 ? 3 : null;
if (!bpp) { console.error('colorType ' + colorType + ' unsupported'); process.exit(1); }

const raw = zlib.inflateSync(Buffer.concat(idat));
const rowBytes = w * bpp;
const stride = rowBytes + 1;

// Un-filter in place into `out`
const out = Buffer.alloc(h * rowBytes);
const prev = Buffer.alloc(rowBytes);
for (let y = 0; y < h; y++) {
    const filter = raw[y * stride];
    const line = raw.subarray(y * stride + 1, (y + 1) * stride);
    const cur = out.subarray(y * rowBytes, (y + 1) * rowBytes);
    for (let i = 0; i < rowBytes; i++) {
        const a = i >= bpp ? cur[i - bpp] : 0;
        const b = prev[i];
        const c = i >= bpp ? prev[i - bpp] : 0;
        let v = line[i];
        switch (filter) {
            case 0: break;
            case 1: v = (v + a) & 0xff; break;
            case 2: v = (v + b) & 0xff; break;
            case 3: v = (v + ((a + b) >> 1)) & 0xff; break;
            case 4: {
                const p = a + b - c;
                const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
                v = (v + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c)) & 0xff;
                break;
            }
        }
        cur[i] = v;
    }
    prev.set(cur);
}

for (const [x, y] of pts) {
    if (x < 0 || x >= w || y < 0 || y >= h) { console.log(`${x},${y}: out of range`); continue; }
    const i = y * rowBytes + x * bpp;
    console.log(`${x},${y}: R=${out[i]} G=${out[i + 1]} B=${out[i + 2]}`);
}