"use client";
import type { Metrics } from "@/lib/types";

const pct = (v: number | null) => (v === null ? "–" : `${v.toFixed(v % 1 === 0 ? 0 : 1)}%`);
const secs = (v: number | null) => (v === null ? "–" : `${v.toFixed(1)}s`);

function Tile({ label, value, accent, note }: { label: string; value: string; accent: string; note?: string }) {
  return (
    <div className="rounded-2xl bg-card px-5 py-4">
      <div className="text-base text-muted">{label}</div>
      <div className="text-5xl font-extrabold tabular-nums leading-tight" style={{ color: accent }}>{value}</div>
      {note && <div className="text-sm text-muted">{note}</div>}
    </div>
  );
}

/** The headline numbers, big enough to read from the back of the room. */
export function MetricsPanel({ metrics }: { metrics: Metrics }) {
  return (
    <div className="grid grid-cols-2 gap-3">
      <Tile label="Warned before reading" value={pct(metrics.warned_before_read_pct)} accent="#35F2A0" note={`${metrics.warned} of ${metrics.delivered} recipients`} />
      <Tile label="Basic phones warned first" value={pct(metrics.feature_phones_warned_before_read_pct)} accent="#35F2A0" note={`${metrics.feature_phones_delivered} basic phones, no app needed`} />
      <Tile label="First phone caught it" value={secs(metrics.first_detection_after_s)} accent="#FF2E88" note="after the blast began" />
      <Tile label="Radar confirmed" value={secs(metrics.confirmed_after_s)} accent="#00E5FF" note="3+ independent phones agreed" />
    </div>
  );
}
