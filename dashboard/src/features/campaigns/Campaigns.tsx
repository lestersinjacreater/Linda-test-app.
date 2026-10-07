"use client";
import type { DashboardState } from "@/lib/state";

const NAMES: Record<string, string> = {
  fake_mpesa: "Fake M-Pesa message", sent_by_mistake: "“Sent by mistake”", prize: "Prize scam", fuliza_upgrade: "Fuliza upgrade",
  kra_refund: "KRA refund", job_fee: "Job fee", loan_fee: "Loan fee", pin_request: "PIN request", phishing_link: "Phishing link", other: "Scam",
};

/** Senders the radar has seen reports about, and the result of the poisoning test. */
export function Campaigns({ state }: { state: DashboardState }) {
  const list = Object.values(state.campaigns);
  return (
    <div className="space-y-3">
      <div className="rounded-2xl bg-card p-4">
        <div className="mb-2 text-base text-muted">Scam numbers</div>
        {list.length === 0 && <div className="text-lg text-muted">None yet</div>}
        {list.map((c) => (
          <div key={c.sender} className="flex items-center gap-3 py-1 text-lg">
            <span className="rounded-full px-3 py-0.5 text-base font-bold text-bg" style={{ background: c.confirmedAfterS === null ? "#FFC940" : "#FF2E88" }}>
              {c.confirmedAfterS === null ? "SUSPECTED" : "CONFIRMED"}
            </span>
            <span className="tabular-nums">{c.sender}</span>
            <span className="text-muted">{NAMES[c.category] ?? c.category}</span>
            <span className="ml-auto text-muted">{c.reports} reports</span>
          </div>
        ))}
      </div>
      {state.poison.attempted && (
        <div className="rounded-2xl bg-card p-4 text-lg">
          <span className="font-bold">Attack test: </span>
          {state.poison.blocked === null ? "20 fake phones are accusing an innocent number…" : state.poison.blocked
            ? <span className="font-bold text-mint">blocked. The innocent number was NOT confirmed.</span>
            : <span className="font-bold text-pink">the attack worked (this must never happen)</span>}
        </div>
      )}
    </div>
  );
}
