"use client";
import { Pill } from "@/components/Card";
import { ShieldMark } from "@/components/ShieldMark";
import { Campaigns } from "@/features/campaigns/Campaigns";
import { ProtectionClock, Timeline } from "@/features/blast-timeline/Timeline";
import { Controls } from "@/features/controls/Controls";
import { MetricsPanel } from "@/features/metrics/MetricsPanel";
import { RadarMap } from "@/features/radar-map/RadarMap";
import { useTelco, type Mode } from "@/lib/useTelco";

const BADGE: Record<Mode, { text: string; tone: "green" | "amber" | "grey" }> = {
  live: { text: "● LIVE", tone: "green" },
  replay: { text: "REPLAY · recorded run, simulator offline", tone: "amber" },
  connecting: { text: "Connecting…", tone: "grey" },
};

/**
 * The simulation (at /simulation; the main page / shows only real numbers): the header and the presenter's buttons, the map of
 * Kenya, and on the right the clock, the four headline numbers, the scam numbers and what is happening now. Every number
 * here comes from the simulated network (docs/EXPLAINED.md), never from real phones.
 */
export default function Page() {
  const { state, mode, metrics } = useTelco();
  const badge = BADGE[mode];
  return (
    <main className="mx-auto flex min-h-screen max-w-[1800px] flex-col gap-4 p-5 lg:h-screen lg:overflow-hidden">
      <header className="flex flex-wrap items-center gap-4 rounded-card bg-card px-6 py-3 shadow-card">
        <ShieldMark />
        <div>
          <div className="text-3xl font-bold tracking-tight text-deep">Linda</div>
          <div className="text-base text-muted">Simulation: a scam blast on a pretend network</div>
        </div>
        <div className="hidden text-lg text-muted 2xl:block 2xl:pl-6">Kaa Rada protects the payment. Linda protects the conversation, earlier.</div>
        <div className="ml-auto flex items-center gap-4">
          <a href="/" className="text-base font-semibold text-deep underline-offset-4 hover:underline">Real numbers</a>
          <Pill tone={badge.tone}>{badge.text}</Pill>
        </div>
      </header>

      <Controls live={mode === "live"} />

      <div className="grid min-h-0 flex-1 grid-rows-[minmax(0,1fr)] gap-4 lg:grid-cols-[minmax(0,1.05fr)_minmax(0,1fr)]">
        <RadarMap state={state} />
        <div className="flex min-h-0 flex-col gap-4 overflow-y-auto pb-1">
          <ProtectionClock state={state} />
          <MetricsPanel metrics={metrics} />
          <Campaigns state={state} />
          <Timeline state={state} />
        </div>
      </div>
    </main>
  );
}
