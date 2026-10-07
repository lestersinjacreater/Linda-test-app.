// Where the simulator lives. NEXT_PUBLIC_* values are baked in at build time (see Dockerfile) and are public:
// the BROWSER connects to them, so they must be addresses the audience's machine can reach.
export const TELCO_API = process.env.NEXT_PUBLIC_TELCO_API || "http://localhost:8000";
// The live-feed address is worked out from the API address (http becomes ws, https becomes wss) unless set explicitly.
export const TELCO_WS = process.env.NEXT_PUBLIC_TELCO_WS || TELCO_API.replace(/^http/, "ws") + "/events";
