"use client";
import { Campaigns } from "@/features/campaigns/Campaigns";
import { ProtectionClock, Timeline } from "@/features/blast-timeline/Timeline";
import { Controls } from "@/features/controls/Controls";
import { MetricsPanel } from "@/features/metrics/MetricsPanel";
import { RadarMap } from "@/features/radar-map/RadarMap";
import { useTelco, type Mode } from "@/lib/useTelco";

const BADGE: Record<Mode, { text: string; color: string }> = {
  live: { text: "LIVE", color: "#35F2A0" },
  replay: { text: "REPLAY (recorded run, simulator offline)", color: "#FFC940" },
  connecting: { text: "CONNECTING…", color: "#6D7899" },
};

/** The single demo screen. Dark, big type, readable from the back of a room. */
export default function Page() {
  const { state, mode, metrics } = useTelco();
  const badge = BADGE[mode];
  return (
    <main className="mx-auto flex min-h-screen max-w-[1800px] flex-col gap-4 p-5 lg:h-screen lg:overflow-hidden">
      <header className="flex items-center gap-4">
        <div className="text-4xl font-black tracking-tight text-cyan">LINDA <span className="text-white">radar</span></div>
        <div className="hidden text-xl text-muted md:block">Kaa Rada stops the payment. Linda stops the conversation.</div>
        <div className="ml-auto rounded-full px-4 py-1.5 text-base font-bold text-bg" style={{ background: badge.color }}>{badge.text}</div>
      </header>

      <div className="grid min-h-0 flex-1 grid-rows-[minmax(0,1fr)] gap-4 lg:grid-cols-[minmax(0,1.05fr)_minmax(0,1fr)]">
        <RadarMap state={state} />
        <div className="flex min-h-0 flex-col gap-4 overflow-hidden">
          <ProtectionClock state={state} />
          <MetricsPanel metrics={metrics} />
          <Campaigns state={state} />
          <Timeline state={state} />
        </div>
      </div>

      <Controls live={mode === "live"} />
    </main>
  );
}
