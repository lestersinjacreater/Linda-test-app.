"use client";
import { Pill } from "@/components/Card";
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
 * The one demo screen ("The Linda dashboard, explained" doc and docs/EXPLAINED.md): the header and the presenter's buttons, the map of
 * Kenya, and on the right the clock, the four headline numbers, the scam numbers and what is happening now.
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
          <div className="text-base text-muted">Live scam protection on Safaricom&apos;s network</div>
        </div>
        <div className="hidden text-lg text-muted 2xl:block 2xl:pl-6">Kaa Rada protects the payment. Linda protects the conversation, earlier.</div>
        <div className="ml-auto"><Pill tone={badge.tone}>{badge.text}</Pill></div>
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

/** Linda's mark: five rounded bars in the green ramp, forming a shield (the logo, docs/design-system.md 10). */
function ShieldMark() {
  const tints = ["#B7E4C7", "#74C69D", "#2DBE6C", "#00A651", "#007A3D"];
  return (
    <svg width="44" height="48" viewBox="0 0 44 48" aria-hidden="true">
      <defs><clipPath id="shield"><path d="M22 2 L40 8 L38 28 Q34 40 22 46 Q10 40 6 28 L4 8 Z" /></clipPath></defs>
      <g clipPath="url(#shield)">
        {tints.map((c, i) => <rect key={c} x={4 + i * 7.2} y="0" width="7.4" height="48" fill={c} />)}
      </g>
    </svg>
  );
}
