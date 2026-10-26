// Where the simulator lives. NEXT_PUBLIC_* values are baked in at build time (see Dockerfile) and are public:
// the BROWSER connects to them, so they must be addresses the audience's machine can reach.
// A relative address ("/telco") means "the same server this page came from" (Caddy in front of everything, e.g. on
// Render): then the build does not need to know the server's address in advance.
export const TELCO_API = process.env.NEXT_PUBLIC_TELCO_API || "http://localhost:8000";

/**
 * The live feed's address: http becomes ws and https becomes wss. A relative TELCO_API is resolved against the page
 * the browser opened ([pageUrl], which defaults to the current page). NEXT_PUBLIC_TELCO_WS overrides it.
 */
export function telcoWs(api: string = TELCO_API, pageUrl?: string, explicit = process.env.NEXT_PUBLIC_TELCO_WS): string {
  if (explicit) return explicit;
  const absolute = api.startsWith("/") ? new URL(api, pageUrl ?? window.location.href).href : api;
  return absolute.replace(/^http/, "ws").replace(/\/$/, "") + "/events";
}
