// The fraud-team console's data (contract 5.15): what real Linda phones reported, with FULL numbers. The page and
// these calls sit behind the presenter password (Caddy); the browser sends the password it already typed.
import { RADAR_API } from "./stats";

export type QueueRow = {
  msisdn: string; number: string; category: string; reports: number; phones: number;
  first_at: number; last_at: number; status: "reported" | "suspected" | "confirmed" | "allowlisted"; new: boolean;
};
export type Console = {
  as_of: number;
  counts: { reports: number; waiting: number; confirmed: number; phones: number };
  queue: QueueRow[];
  patterns: Record<string, number>;
  feed: { at: number; kind: "report" | "confirmed"; number: string; category: string }[];
};

const base = (api: string) => `${api.replace(/\/$/, "")}/v1/fraud-team`;

export async function fetchConsole(api: string = RADAR_API): Promise<Console> {
  const r = await fetch(`${base(api)}/console`, { cache: "no-store" });
  if (!r.ok) throw new Error(`gateway answered ${r.status}`);
  return r.json();
}

/** "confirm": a person confirms the scam. "clear": a person says it is not a scam. */
export async function decide(action: "confirm" | "clear", msisdn: string, api: string = RADAR_API): Promise<void> {
  const r = await fetch(`${base(api)}/${action}`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ msisdn }),
  });
  if (!r.ok) throw new Error((await r.json().catch(() => null))?.detail ?? `gateway answered ${r.status}`);
}

/** Waiting means a person still has to look at it. */
export const isWaiting = (row: QueueRow) => row.status === "reported" || row.status === "suspected";

/** The sentence for one line of "Live activity". */
export function feedLine(f: Console["feed"][number], name: (c: string) => string): string {
  return f.kind === "confirmed"
    ? `${f.number} confirmed as a scam (${name(f.category)}). Sent to every Linda phone.`
    : `${f.number} reported by a customer: ${name(f.category)}.`;
}
