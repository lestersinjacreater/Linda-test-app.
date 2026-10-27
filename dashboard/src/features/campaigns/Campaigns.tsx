"use client";
import { Card, Pill } from "@/components/Card";
import { CATEGORY_NAMES } from "@/lib/categories";
import type { DashboardState } from "@/lib/state";

const NAMES = CATEGORY_NAMES;

/** The scam numbers the gateway has heard about, and the result of the poisoning test. */
export function Campaigns({ state }: { state: DashboardState }) {
  const list = Object.values(state.campaigns);
  return (
    <div className="space-y-3">
      <Card title="Scam numbers">
        {list.length === 0 && <div className="text-lg text-muted">None yet</div>}
        {list.map((c) => (
          <div key={c.sender} className="flex flex-wrap items-center gap-3 py-1 text-lg">
            {c.confirmedAfterS === null
              ? <Pill tone="amber">SUSPECTED</Pill>
              : <Pill tone="red">{c.known ? "KNOWN" : "CONFIRMED"}</Pill>}
            <span className="tabular-nums text-ink">{c.sender}</span>
            {!c.known && <span className="text-muted">{NAMES[c.category] ?? c.category}</span>}
            <span className="ml-auto text-muted">{c.known ? "already known before the first call" : `${c.reports} reports`}</span>
          </div>
        ))}
      </Card>
      {state.poison.attempted && (
        <Card>
          <div className="text-lg">
            <span className="font-semibold">Attack test: </span>
            {state.poison.blocked === null ? "20 fake phones are accusing an innocent number…" : state.poison.blocked
              ? <span className="font-semibold text-deep">blocked. The innocent number was NOT confirmed.</span>
              : <span className="font-semibold text-red">the attack worked (this must never happen)</span>}
          </div>
        </Card>
      )}
    </div>
  );
}
