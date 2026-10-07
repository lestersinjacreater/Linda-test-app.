"use client";
import { STATUS_COLOR, type DashboardState, type PhoneStatus } from "@/lib/state";
import { MAP_H, MAP_W, OUTLINE_PATH, TOWN_LABELS, project } from "./kenya";

const LEGEND: { status: PhoneStatus; label: string }[] = [
  { status: "idle", label: "Quiet" },
  { status: "received", label: "Got the scam" },
  { status: "detected", label: "Linda caught it" },
  { status: "warned", label: "Warned" },
];

/** Kenya with one dot per phone: grey idle, amber received, red detected on the device, green warned. */
export function RadarMap({ state }: { state: DashboardState }) {
  const phones = Object.values(state.phones);
  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden rounded-3xl bg-card p-4">
      <svg viewBox={`0 0 ${MAP_W} ${MAP_H}`} preserveAspectRatio="xMidYMid meet" className="min-h-0 w-full flex-1" role="img" aria-label="Map of Kenya with phones">
        <path d={OUTLINE_PATH} fill="#10162A" stroke="#2B3556" strokeWidth={2} />
        {TOWN_LABELS.map((t) => {
          const [x, y] = project(t.lon, t.lat);
          return <text key={t.name} x={x + 10} y={y - 8} fill="#6D7899" fontSize={14}>{t.name}</text>;
        })}
        {phones.map((p) => {
          const [x, y] = project(p.lon, p.lat);
          // Basic phones are drawn as squares so the room can see they are protected too.
          return p.kind === "feature" ? (
            <rect key={p.phone} className="dot" x={x - 4.5} y={y - 4.5} width={9} height={9} rx={2} fill={STATUS_COLOR[p.status]} />
          ) : (
            <circle key={p.phone} className="dot" cx={x} cy={y} r={p.kind === "linda" ? 6.5 : 5.5} fill={STATUS_COLOR[p.status]}
              stroke={p.kind === "linda" ? "#00E5FF" : "none"} strokeWidth={1.5} />
          );
        })}
      </svg>
      <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-2 text-base text-muted">
        {LEGEND.map((l) => (
          <span key={l.status} className="flex items-center gap-2">
            <span className="inline-block h-3.5 w-3.5 rounded-full" style={{ background: STATUS_COLOR[l.status] }} />
            {l.label}
          </span>
        ))}
        <span className="ml-auto text-sm">● smartphone · ◉ Linda phone · ■ basic phone</span>
      </div>
    </div>
  );
}
