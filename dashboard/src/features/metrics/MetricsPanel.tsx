"use client";
import { runNotes } from "@/lib/state";
import type { Metrics } from "@/lib/types";

const pct = (v: number | null) => (v === null ? "–" : `${v.toFixed(v % 1 === 0 ? 0 : 1)}%`);
const secs = (v: number | null) => (v === null ? "–" : `${v.toFixed(1)}s`);

/** One headline number: a plain label, the number big, one line of context. */
function Tile({ label, value, tone, note }: { label: string; value: string; tone: "green" | "red" | "deep"; note?: string }) {
  const color = { green: "text-green", red: "text-red", deep: "text-deep" }[tone];
  return (
    <div className="rounded-card bg-card px-5 py-3 shadow-card">
      <div className="text-base text-muted">{label}</div>
      <div className={`text-4xl font-bold leading-tight tracking-tight tabular-nums ${color}`}>{value}</div>
      {note && <div className="text-sm text-muted">{note}</div>}
    </div>
  );
}

/** The four headline numbers, big enough to read from the back of the room, and a line about the run. */
export function MetricsPanel({ metrics }: { metrics: Metrics }) {
  const notes = runNotes(metrics);
  return (
    <div className="flex flex-col gap-3">
      <div className="grid grid-cols-2 gap-3">
        <Tile label="Warned before reading" value={pct(metrics.warned_before_read_pct)} tone="green" note={`${metrics.warned} of ${metrics.delivered} people who got it`} />
        <Tile label="Basic phones warned first" value={pct(metrics.feature_phones_warned_before_read_pct)} tone="green" note={`${metrics.feature_phones_delivered} basic phones, no app needed`} />
        <Tile label="First Linda phone caught it" value={secs(metrics.first_detection_after_s)} tone="red" note="after the scam started" />
        <Tile label="Safaricom confirmed the number" value={secs(metrics.confirmed_after_s)} tone="deep" note={metrics.delivered === 0 && metrics.confirmed_after_s === 0 ? "already known before the first call" : "3 or more phones agreed"} />
      </div>
      {notes.map((n) => <div key={n} className="rounded-2xl bg-mint px-4 py-2 text-lg text-forest">{n}</div>)}
    </div>
  );
}
