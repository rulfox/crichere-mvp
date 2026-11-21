import type { NextConfig } from "next";

/*
 * Static export (docs/PHASE20.md section 5): plain files, no Node server. Security headers are set by
 * the host (Caddyfile), since a static export can't send headers itself.
 */
const nextConfig: NextConfig = {
  output: "export",
  trailingSlash: true,
  images: { unoptimized: true },
  poweredByHeader: false,
};

export default nextConfig;
