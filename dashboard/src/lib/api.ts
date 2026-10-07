import { TELCO_API } from "./config";
import type { Metrics, Phone } from "./types";

async function json<T>(path: string, init?: RequestInit): Promise<T | null> {
  try {
    const r = await fetch(`${TELCO_API}${path}`, { ...init, cache: "no-store" });
    return r.ok ? ((await r.json()) as T) : null;
  } catch {
    return null; // the simulator is unreachable: callers fall back to the replay
  }
}

export const getPopulation = () => json<Phone[]>("/population");
export const getMetrics = (sender?: string | null) => json<Metrics>(`/metrics${sender ? `?sender=${sender}` : ""}`);

export type Scenario = "blast" | "rotating" | "poison";
export const startScenario = (s: Scenario) => json<unknown>(`/scenarios/${s}`, { method: "POST" });
export const resetSimulation = () => json<unknown>("/reset", { method: "POST" });
