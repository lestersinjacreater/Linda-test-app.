"use client";
import { useCallback, useEffect, useState } from "react";
import { Card, Pill } from "@/components/Card";
import { ShieldMark } from "@/components/ShieldMark";
import { categoryName } from "@/lib/categories";
import { decide, feedLine, fetchConsole, isWaiting, type Console, type QueueRow } from "@/lib/fraudteam";
import { ago, bars } from "@/lib/stats";

const EVERY_MS = 5_000;

const STATUS: Record<QueueRow["status"], { text: string; tone: "red" | "amber" | "green" | "grey" }> = {
  reported: { text: "Waiting for review", tone: "amber" },
  suspected: { text: "Suspected", tone: "amber" },
  confirmed: { text: "Confirmed scam", tone: "red" },
  allowlisted: { text: "Not a scam", tone: "green" },
};

/**
 * The fraud-team console (contract 5.15; design idea from Kelvin's prototype): the screen a Safaricom fraud analyst
 * works from. Every row is a real report from a real Linda phone. A person confirms a number (every Linda phone gets
 * it at its next sync, and the network warns everyone who heard from it) or clears it. Behind the presenter password.
 */
export default function FraudTeamPage() {
  const [data, setData] = useState<Console | null>(null);
  const [down, setDown] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const [gotAt, setGotAt] = useState(0);

  const load = useCallback(() => fetchConsole()
    .then((d) => { setData(d); setGotAt(Date.now()); setDown(false); })
    .catch(() => setDown(true)), []);

  useEffect(() => {
    load();
    const a = setInterval(load, EVERY_MS);
    const b = setInterval(() => setNow(Date.now()), 1000);
    return () => { clearInterval(a); clearInterval(b); };
  }, [load]);

  const act = async (action: "confirm" | "clear", row: QueueRow) => {
    setBusy(row.msisdn);
    try {
      await decide(action, row.msisdn);
      setNote(action === "confirm"
        ? `${row.number} confirmed. Every Linda phone gets it at its next sync, and everyone who heard from it is warned.`
        : `${row.number} marked as not a scam. Phones drop it at their next sync.`);
      await load();
    } catch (e) {
      setNote(`Could not do that: ${(e as Error).message}`);
    } finally {
      setBusy(null);
    }
  };

  const serverNow = data ? data.as_of + (now - gotAt) / 1000 : 0;
  const c = data?.counts;

  return (
    <main className="mx-auto flex min-h-screen max-w-[1400px] flex-col gap-5 p-5 lg:p-8">
      <header className="flex flex-wrap items-center gap-4 rounded-card bg-card px-6 py-4 shadow-card">
        <ShieldMark />
        <div>
          <div className="text-3xl font-bold tracking-tight text-deep">Linda fraud-team console</div>
          <div className="text-base text-muted">
            What Safaricom&apos;s fraud team sees. Confirming a number sends it to every Linda phone.
          </div>
        </div>
        <div className="ml-auto flex items-center gap-4">
          <a href="/" className="text-base font-semibold text-deep underline-offset-4 hover:underline">Real numbers</a>
          {down ? <Pill tone="red">Can&apos;t reach the server</Pill> : data ? <Pill>● LIVE</Pill> : <Pill tone="grey">Connecting…</Pill>}
        </div>
      </header>

      {note && (
        <div className="flex items-center gap-4 rounded-card bg-mint px-6 py-3 text-lg text-deep">
          <span>{note}</span>
          <button className="ml-auto text-base font-semibold underline" onClick={() => setNote(null)}>Close</button>
        </div>
      )}

      <section className="grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi label="Customer reports" value={c?.reports} />
        <Kpi label="Waiting for review" value={c?.waiting} hot={!!c?.waiting} />
        <Kpi label="Confirmed scam numbers" value={c?.confirmed} />
        <Kpi label="Linda phones reporting in" value={c?.phones} />
      </section>

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <Card className="min-w-0" title="Review queue" right={<span className="text-base text-muted">Waiting first, newest activity first</span>}>
          {!data ? <Empty>Waiting for the server…</Empty>
            : data.queue.length === 0 ? <Empty>No reports yet. When a customer taps Report in the Linda app, the number appears here within seconds.</Empty>
            : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[720px] text-left text-lg">
                  <thead className="text-sm uppercase tracking-wide text-muted">
                    <tr><th className="py-2 font-semibold">Number</th><th className="font-semibold">Scam type</th>
                      <th className="text-right font-semibold">Reports</th><th className="pl-4 font-semibold">Status</th><th /></tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {data.queue.map((row) => (
                      <tr key={row.msisdn} className={row.new && isWaiting(row) ? "bg-amberTint/40" : ""}>
                        <td className="whitespace-nowrap py-3 font-mono tabular-nums text-ink">
                          {row.number}{row.new && <span className="ml-2 rounded-full bg-amberTint px-2 py-0.5 font-sans text-xs font-bold text-amber">NEW</span>}
                          <div className="font-sans text-sm text-muted">last report {ago(row.last_at, serverNow)}</div>
                        </td>
                        <td className="text-ink">{categoryName(row.category)}</td>
                        <td className="text-right tabular-nums text-ink">
                          {row.reports}<div className="text-sm text-muted">{row.phones} {row.phones === 1 ? "phone" : "phones"}</div>
                        </td>
                        <td className="whitespace-nowrap pl-4"><Pill tone={STATUS[row.status].tone}>{STATUS[row.status].text}</Pill></td>
                        <td className="py-3 pl-3 text-right">
                          {isWaiting(row) ? (
                            <div className="flex justify-end gap-2">
                              <button disabled={busy === row.msisdn} onClick={() => act("confirm", row)}
                                className="whitespace-nowrap rounded-full bg-deep px-4 py-2 text-base font-semibold text-white disabled:opacity-50">Confirm scam</button>
                              <button disabled={busy === row.msisdn} onClick={() => act("clear", row)}
                                className="whitespace-nowrap rounded-full border border-line px-4 py-2 text-base font-semibold text-ink disabled:opacity-50">Not a scam</button>
                            </div>
                          ) : row.status === "confirmed" ? <span className="text-base text-muted">Sent to all phones</span> : null}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
        </Card>

        <div className="flex min-w-0 flex-col gap-5">
          <Card title="Scam patterns">
            {!data || bars(data.patterns).length === 0 ? <Empty>No reports yet.</Empty> : (
              <ul className="space-y-3">
                {bars(data.patterns).map((b) => (
                  <li key={b.category}>
                    <div className="mb-1 flex text-lg"><span className="text-ink">{categoryName(b.category)}</span>
                      <span className="ml-auto font-semibold tabular-nums text-ink">{b.count}</span></div>
                    <div className="h-3 rounded-full bg-raised"><div className="h-3 rounded-full bg-green" style={{ width: `${b.width}%` }} /></div>
                  </li>
                ))}
              </ul>
            )}
          </Card>
          <Card title="Live activity">
            {!data || data.feed.length === 0 ? <Empty>Nothing yet.</Empty> : (
              <ul className="space-y-3">
                {data.feed.map((f, i) => (
                  <li key={`${f.at}-${i}`} className="flex gap-3 text-base">
                    <span className="w-20 shrink-0 tabular-nums text-muted">{ago(f.at, serverNow)}</span>
                    <span className={f.kind === "confirmed" ? "font-semibold text-red" : "text-ink"}>{feedLine(f, categoryName)}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>

      <p className="text-base text-muted">
        Reports come only from real Linda phones and never contain message text: the number, the scam type and when.
        Three independent phones confirm a number on their own; here a person can confirm it sooner, or clear it.
      </p>
    </main>
  );
}

function Kpi({ label, value, hot = false }: { label: string; value?: number; hot?: boolean }) {
  return (
    <div className={`rounded-card bg-card p-6 shadow-card ${hot ? "ring-2 ring-amberDot" : ""}`}>
      <div className="text-5xl font-bold tabular-nums tracking-tight text-deep">{value === undefined ? "–" : value}</div>
      <div className="mt-2 text-lg text-muted">{label}</div>
    </div>
  );
}

function Empty({ children }: { children: React.ReactNode }) {
  return <p className="py-6 text-lg text-muted">{children}</p>;
}
