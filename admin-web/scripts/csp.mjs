// Writes deploy/Caddyfile from deploy/Caddyfile.template after `next build`, with a Content-Security-
// Policy that allows exactly the inline scripts Next.js put into the exported HTML (by SHA-256) and
// nothing else inline. A static host can't use nonces, so hashes are the strict option.
import { createHash } from "node:crypto";
import { readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const OUT = "out";
const api = (process.env.NEXT_PUBLIC_API_BASE ?? "").replace(/\/$/, "");
if (!api) {
  console.error("NEXT_PUBLIC_API_BASE must be set when building for a host.");
  process.exit(1);
}

const html = [];
const walk = (dir) => {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) walk(path);
    else if (name.endsWith(".html")) html.push(path);
  }
};
walk(OUT);

const hashes = new Set();
for (const file of html) {
  const text = readFileSync(file, "utf8");
  for (const match of text.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/g)) {
    if (match[1].length === 0) continue;
    hashes.add(`'sha256-${createHash("sha256").update(match[1], "utf8").digest("base64")}'`);
  }
}

const gsi = "https://accounts.google.com/gsi/";
const csp = [
  "default-src 'self'",
  `script-src 'self' ${[...hashes].sort().join(" ")} ${gsi}client`,
  // React sets style attributes; Google's button loads its own stylesheet.
  `style-src 'self' 'unsafe-inline' ${gsi}style`,
  "font-src 'self'",
  // Profile photos, league images and evidence live in S3; payment proofs are presigned S3 links.
  "img-src 'self' data: https://*.amazonaws.com https://lh3.googleusercontent.com",
  `connect-src 'self' ${api} ${gsi}`,
  `frame-src ${gsi}`,
  "frame-ancestors 'none'",
  "base-uri 'none'",
  "form-action 'self'",
  "object-src 'none'",
].join("; ");

const template = readFileSync("deploy/Caddyfile.template", "utf8");
writeFileSync("deploy/Caddyfile", template.replace("{{CSP}}", csp));
console.log(`CSP written with ${hashes.size} inline script hash(es) from ${html.length} page(s).`);
