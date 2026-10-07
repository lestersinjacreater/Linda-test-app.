// All dashboard state is rebuilt from the event stream by one pure function, so it is easy to test and
// the live feed and the offline replay behave identically.
import type { Metrics, Phone, SimEvent } from "./types";

/** idle (grey) -> received (amber) -> detected (red) -> warned (green) */
export type PhoneStatus = "idle" | "received" | "detected" | "warned";

export interface PhoneState extends Phone {
  status: PhoneStatus;
  delivered: boolean;
  warnedBeforeRead: boolean;
}

export interface Campaign {
  sender: string;
  category: string;
  reports: number;
  confirmedAfterS: number | null;
}

export interface DashboardState {
  phones: Record<number, PhoneState>;
  feed: SimEvent[];            // newest first, trimmed
  campaigns: Record<string, Campaign>;
  simTime: number;
  running: boolean;
  poison: { attempted: boolean; blocked: boolean | null };
  blastStart: Record<string, number>;
  firstDetection: Record<string, number>; // sender -> sim time of the first on-device detection
  latestSender: string | null;  // the most recent real blast (not an attack): what the headline numbers are about
}

export const FEED_LIMIT = 120;

export function initialState(population: Phone[] = []): DashboardState {
  const phones: Record<number, PhoneState> = {};
  for (const p of population) phones[p.phone] = { ...p, status: "idle", delivered: false, warnedBeforeRead: false };
  return { phones, feed: [], campaigns: {}, simTime: 0, running: false, poison: { attempted: false, blocked: null }, blastStart: {}, firstDetection: {}, latestSender: null };
}

/** Adds phones the state does not know yet (from GET /population), without touching the ones it does. */
export function withPopulation(state: DashboardState, population: Phone[]): DashboardState {
  const phones = { ...state.phones };
  for (const p of population) if (!phones[p.phone]) phones[p.phone] = { ...p, status: "idle", delivered: false, warnedBeforeRead: false };
  return { ...state, phones };
}

/** Phones keep their place on the map; everything else goes back to the start. */
function cleared(state: DashboardState): DashboardState {
  return initialState(Object.values(state.phones).map(({ phone, kind, town, lat, lon }) => ({ phone, kind, town, lat, lon })));
}

const RANK: Record<PhoneStatus, number> = { idle: 0, received: 1, detected: 2, warned: 3 };

function withStatus(state: DashboardState, e: SimEvent, status: PhoneStatus, patch: Partial<PhoneState> = {}): DashboardState["phones"] {
  if (e.phone === undefined) return state.phones;
  const existing = state.phones[e.phone] ?? {
    phone: e.phone, kind: e.kind ?? "feature", town: e.town ?? "", lat: e.lat ?? 0, lon: e.lon ?? 0,
    status: "idle" as PhoneStatus, delivered: false, warnedBeforeRead: false,
  };
  // A phone never goes backwards (a late "received" cannot undo "warned").
  const next = RANK[status] > RANK[existing.status] ? status : existing.status;
  return { ...state.phones, [e.phone]: { ...existing, ...patch, status: next } };
}

export function reduce(state: DashboardState, e: SimEvent): DashboardState {
  if (e.type === "reset") return cleared(state);

  let next: DashboardState = { ...state, simTime: e.sim_time, feed: [e, ...state.feed].slice(0, FEED_LIMIT) };

  switch (e.type) {
    case "blast_started":
      next.running = true;
      if (e.attack) next.poison = { attempted: true, blocked: null };
      if (e.sender) next.blastStart = { ...next.blastStart, [e.sender]: e.sim_time };
      if (e.sender && !e.attack) next.latestSender = e.sender;
      break;
    case "blast_finished":
      next.running = false;
      if (e.attack) next.poison = { attempted: true, blocked: e.poison_blocked ?? null };
      break;
    case "sms_delivered":
      next.phones = withStatus(next, e, "received", { delivered: true });
      break;
    case "linda_detected":
      next.phones = withStatus(next, e, "detected", e.before_read ? { warnedBeforeRead: true } : {});
      if (e.sender && next.firstDetection[e.sender] === undefined) next.firstDetection = { ...next.firstDetection, [e.sender]: e.sim_time };
      break;
    case "warning_delivered":
      next.phones = withStatus(next, e, "warned", e.before_read ? { warnedBeforeRead: true } : {});
      break;
    case "report_sent":
      if (e.sender && !e.attack) {
        const c = next.campaigns[e.sender] ?? { sender: e.sender, category: "other", reports: 0, confirmedAfterS: null };
        next.campaigns = { ...next.campaigns, [e.sender]: { ...c, reports: c.reports + 1 } };
      }
      break;
    case "sender_confirmed":
      if (e.sender) {
        const c = next.campaigns[e.sender] ?? { sender: e.sender, category: "other", reports: 0, confirmedAfterS: null };
        next.campaigns = { ...next.campaigns, [e.sender]: { ...c, confirmedAfterS: e.confirmed_after_s ?? null } };
      }
      break;
  }
  if (e.type === "linda_detected" && e.sender && e.category) {
    const c = next.campaigns[e.sender] ?? { sender: e.sender, category: e.category, reports: 0, confirmedAfterS: null };
    next.campaigns = { ...next.campaigns, [e.sender]: { ...c, category: e.category } };
  }
  return next;
}

/** The headline numbers, computed from what the screen has seen. Used for the offline replay; live mode uses GET /metrics. */
export function metricsFromState(state: DashboardState, sender: string | null): Metrics {
  const phones = Object.values(state.phones).filter((p) => p.delivered);
  const warned = phones.filter((p) => p.status === "detected" || p.status === "warned");
  const feature = phones.filter((p) => p.kind === "feature");
  const pct = (n: number, d: number) => (d ? Math.round((1000 * n) / d) / 10 : null);
  const campaign = sender ? state.campaigns[sender] : undefined;
  return {
    sender, run: 0,
    delivered: phones.length,
    warned: warned.length,
    warned_before_read_pct: pct(warned.filter((p) => p.warnedBeforeRead).length, phones.length),
    feature_phones_delivered: feature.length,
    feature_phones_warned_before_read_pct: pct(feature.filter((p) => p.warnedBeforeRead).length, feature.length),
    first_detection_after_s: sender && state.firstDetection[sender] !== undefined ? Math.round((state.firstDetection[sender] - (state.blastStart[sender] ?? 0)) * 100) / 100 : null,
    confirmed_after_s: campaign?.confirmedAfterS ?? null,
    warned_by_linda: 0,
    warned_by_network: 0,
  };
}

/** One line of plain English for the live feed. Never contains message text (the simulator never sends it). */
export function describe(e: SimEvent): string {
  const who = e.kind === "feature" ? "Basic phone" : e.kind === "linda" ? "Linda phone" : "Smartphone";
  const where = e.town ? ` in ${e.town}` : "";
  switch (e.type) {
    case "reset": return "Reset";
    case "blast_started": return e.attack ? `Poisoning attack on ${e.sender}` : `Scam blast started from ${e.sender}`;
    case "sms_delivered": return `${who}${where} received the scam`;
    case "linda_detected": return `${who}${where} caught it on the device (${e.category ?? "scam"})`;
    case "report_sent": return e.attack ? `Fake device accused ${e.sender}: radar says "${e.status}"` : `Report sent (no message text): radar says "${e.status}", ${e.devices ?? 0} devices`;
    case "report_failed": return "Report could not reach the radar (kept for retry)";
    case "sender_confirmed": return `RADAR CONFIRMED ${e.sender} after ${e.confirmed_after_s}s`;
    case "warning_delivered": return `${who}${where} warned by the network${e.before_read ? " before reading" : ""}`;
    case "blast_finished": return e.attack ? (e.poison_blocked ? "Attack blocked: innocent number NOT confirmed" : "Attack succeeded (bad!)") : "Blast finished";
  }
}

/** Colours for each phone status, shared by the map and its legend. */
export const STATUS_COLOR: Record<PhoneStatus, string> = {
  idle: "#4A5470",
  received: "#FFC940",
  detected: "#FF2E88",
  warned: "#35F2A0",
};
