"use client";
import { describe, type DashboardState } from "@/lib/state";
import type { EventType } from "@/lib/types";

const DOT: Record<EventType, string> = {
  reset: "#6D7899", blast_started: "#FFC940", sms_delivered: "#FFC940", linda_detected: "#FF2E88", report_sent: "#00E5FF",
  report_failed: "#FF2E88", sender_confirmed: "#00E5FF", warning_delivered: "#35F2A0", blast_finished: "#6D7899",
};

/** The big clock: seconds since the scam started, and how long until the network protected people. */
export function ProtectionClock({ state }: { state: DashboardState }) {
  const start = state.latestSender ? state.blastStart[state.latestSender] ?? 0 : 0;
  const elapsed = Math.max(0, state.simTime - start);
  const confirmed = state.latestSender ? state.campaigns[state.latestSender]?.confirmedAfterS ?? null : null;
  return (
    <div className="flex items-baseline gap-4 rounded-2xl bg-card px-5 py-3">
      <div className="text-base text-muted">Time since the scam started</div>
      <div className="text-5xl font-extrabold tabular-nums text-amber">{elapsed.toFixed(0)}s</div>
      <div className="ml-auto text-xl font-bold" style={{ color: confirmed === null ? "#6D7899" : "#00E5FF" }}>
        {confirmed === null ? (state.running ? "radar listening…" : "waiting") : `radar confirmed at ${confirmed.toFixed(0)}s`}
      </div>
    </div>
  );
}

/** Scrolling plain-English feed of what is happening. Newest on top. */
export function Timeline({ state }: { state: DashboardState }) {
  return (
    <div className="min-h-0 flex-1 overflow-hidden rounded-2xl bg-card p-4">
      <ul className="space-y-1.5">
        {state.feed.slice(0, 14).map((e, i) => (
          <li key={`${e.sim_time}-${e.type}-${e.phone ?? i}-${i}`} className="flex items-center gap-3 text-lg leading-snug" style={{ opacity: 1 - i * 0.05 }}>
            <span className="inline-block h-3 w-3 shrink-0 rounded-full" style={{ background: DOT[e.type] }} />
            <span className="w-14 shrink-0 text-right tabular-nums text-muted">{e.sim_time.toFixed(0)}s</span>
            <span className={e.type === "sender_confirmed" ? "font-bold text-cyan" : ""}>{describe(e)}</span>
          </li>
        ))}
        {state.feed.length === 0 && <li className="text-lg text-muted">Quiet network. Press “Start blast” (or B).</li>}
      </ul>
    </div>
  );
}
