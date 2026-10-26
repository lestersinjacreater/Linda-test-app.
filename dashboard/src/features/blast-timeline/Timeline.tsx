"use client";
import { Card, Pill } from "@/components/Card";
import { describe, type DashboardState } from "@/lib/state";
import type { EventType } from "@/lib/types";

const DOT: Record<EventType, string> = {
  reset: "#C9D3CD", blast_started: "#F2A900", sms_delivered: "#F2A900", linda_detected: "#E4002B", report_sent: "#007A3D",
  report_failed: "#E4002B", sender_confirmed: "#007A3D", warning_delivered: "#00A651", blast_finished: "#C9D3CD",
  call_attempt: "#F2A900", call_labelled: "#007A3D",
};

/** The big clock: seconds since the scam started, and whether Safaricom has confirmed the number yet. */
export function ProtectionClock({ state }: { state: DashboardState }) {
  const start = state.latestSender ? state.blastStart[state.latestSender] ?? 0 : 0;
  const elapsed = Math.max(0, state.simTime - start);
  const confirmed = state.latestSender ? state.campaigns[state.latestSender]?.confirmedAfterS ?? null : null;
  return (
    <div className="flex flex-wrap items-center gap-4 rounded-card bg-card px-5 py-3 shadow-card">
      <div className="text-base text-muted">Time since the scam started</div>
      <div className="text-4xl font-bold tracking-tight tabular-nums text-ink">{elapsed.toFixed(0)}s</div>
      <div className="ml-auto">
        {confirmed === null
          ? <Pill tone="grey">{state.running ? "Listening for Linda phones…" : "Waiting"}</Pill>
          : <Pill tone="green">Confirmed at {confirmed.toFixed(0)}s</Pill>}
      </div>
    </div>
  );
}

/** What is happening, in plain English, newest on top. */
export function Timeline({ state }: { state: DashboardState }) {
  return (
    <Card title="What is happening" className="min-h-0 flex-1 overflow-hidden">
      <ul className="space-y-1.5">
        {state.feed.slice(0, 14).map((e, i) => (
          <li key={`${e.sim_time}-${e.type}-${e.phone ?? i}-${i}`} className="flex items-center gap-3 text-lg leading-snug" style={{ opacity: 1 - i * 0.05 }}>
            <span className="inline-block h-3 w-3 shrink-0 rounded-full" style={{ background: DOT[e.type] }} />
            <span className="w-14 shrink-0 text-right tabular-nums text-muted">{e.sim_time.toFixed(0)}s</span>
            <span className={e.type === "sender_confirmed" ? "font-semibold text-deep" : "text-ink"}>{describe(e)}</span>
          </li>
        ))}
        {state.feed.length === 0 && <li className="text-lg text-muted">Quiet network. Press “Start blast” (or B).</li>}
      </ul>
    </Card>
  );
}
