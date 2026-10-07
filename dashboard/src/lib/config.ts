// Where the simulator lives. NEXT_PUBLIC_* values are baked in at build time (see Dockerfile).
export const TELCO_API = process.env.NEXT_PUBLIC_TELCO_API ?? "http://localhost:8000";
export const TELCO_WS = process.env.NEXT_PUBLIC_TELCO_WS ?? "ws://localhost:8000/events";
