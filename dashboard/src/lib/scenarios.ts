// The presenter's buttons (docs/demo-script.md). Pure data, so the query each one sends is easy to test.

export type Scenario = "blast" | "rotating" | "poison" | "call";

/** A number the mock fraud team has already blocked (fraudintel/data/seed.json): calls from it are known at once. */
export const FRAUD_TEAM_NUMBER = "254700000801";

export interface Preset {
  key: string;            // the hotkey
  label: string;
  scenario: Scenario | "reset";
  params?: Record<string, string>;
  color: string;
}

export const PRESETS: Preset[] = [
  { key: "r", label: "Reset", scenario: "reset", color: "#6D7899" },
  { key: "b", label: "Start blast", scenario: "blast", color: "#FF2E88" },
  // The same 500-person blast in a 2,000-phone country, with 0.5%, 1% or 2% of people running Linda.
  { key: "1", label: "0.5% adoption", scenario: "blast", params: { adoption: "0.5" }, color: "#FF8FC2" },
  { key: "2", label: "1% adoption", scenario: "blast", params: { adoption: "1" }, color: "#FF8FC2" },
  { key: "3", label: "2% adoption", scenario: "blast", params: { adoption: "2" }, color: "#FF8FC2" },
  { key: "c", label: "Scam calls", scenario: "call", color: "#B18CFF" },
  { key: "f", label: "Fraud-team number calls", scenario: "call", params: { caller: FRAUD_TEAM_NUMBER }, color: "#B18CFF" },
  { key: "o", label: "Rotating numbers", scenario: "rotating", color: "#FFC940" },
  { key: "p", label: "Poison test", scenario: "poison", color: "#00E5FF" },
];

/** The simulator path for a preset, e.g. /scenarios/blast?adoption=1 */
export function presetPath(p: Preset): string {
  if (p.scenario === "reset") return "/reset";
  const q = new URLSearchParams(p.params ?? {}).toString();
  return `/scenarios/${p.scenario}${q ? `?${q}` : ""}`;
}
