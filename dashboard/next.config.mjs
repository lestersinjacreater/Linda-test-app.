/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone", // small Docker image
  // The main page asks "/radar/v1/stats" on the server it came from. On a server
  // Caddy answers /radar itself; on a laptop (npm run dev, docker compose) this
  // passes it on to the gateway. Fixed at build time.
  async rewrites() {
    const radar = process.env.RADAR_UPSTREAM || "http://localhost:8001";
    return [{ source: "/radar/:path*", destination: `${radar}/:path*` }];
  },
};
export default nextConfig;
