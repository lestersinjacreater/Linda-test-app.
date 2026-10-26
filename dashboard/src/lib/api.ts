import { TELCO_API } from "./config";
import { presetPath, type Preset } from "./scenarios";
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

/** Runs one presenter button: a scenario (with its adoption level or caller) or a reset. */
export const runPreset = (p: Preset) => json<unknown>(presetPath(p), { method: "POST" });
