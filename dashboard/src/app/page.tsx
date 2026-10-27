"use client";
import { useEffect, useState } from "react";
import { Card, Pill } from "@/components/Card";
import { ShieldMark } from "@/components/ShieldMark";
import { categoryName } from "@/lib/categories";
import { ago, bars, fetchDownloads, fetchStats, type Stats } from "@/lib/stats";

const STATS_EVERY_MS = 10_000;
const DOWNLOADS_EVERY_MS = 5 * 60_000; // GitHub: 60 unsigned requests an hour per network

const STATUS: Record<string, { text: string; tone: "red" | "amber" | "grey" }> = {
  confirmed: { text: "Confirmed scam", tone: "red" },
  suspected: { text: "Suspected", tone: "amber" },
};

/**
 * The main page: real numbers only, from real Linda phones (contract 5.14). Simulated phones are left out by the
 * gateway. The simulation lives at /simulation. When the gateway cannot be reached the page says so, never guesses.
 */
export default function Page() {
  const [stats, setStats] = useState<Stats | null>(null);
  const [statsAt, setStatsAt] = useState<number | null>(null); // when the last good answer came (ms)
  const [down, setDown] = useState(false);
  const [downloads, setDownloads] = useState<number | null>(null);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    let alive = true;
    const load = () => fetchStats()
      .then((s) => { if (alive) { setStats(s); setStatsAt(Date.now()); setDown(false); } })
      .catch(() => { if (alive) setDown(true); });
    const loadDownloads = () => fetchDownloads().then((n) => { if (alive) setDownloads(n); }).catch(() => {});
    load();
    loadDownloads();
    const a = setInterval(load, STATS_EVERY_MS);
    const b = setInterval(loadDownloads, DOWNLOADS_EVERY_MS);
    const c = setInterval(() => setNow(Date.now()), 1000);
    return () => { alive = false; clearInterval(a); clearInterval(b); clearInterval(c); };
  }, []);

  const updated = statsAt === null ? null : Math.round((now - statsAt) / 1000);
  // Server time for "4 min ago": the gateway's clock at the answer, moved on by the seconds since.
  const serverNow = stats ? stats.as_of + (updated ?? 0) : 0;

  return (
    <main className="mx-auto flex min-h-screen max-w-[1400px] flex-col gap-5 p-5 lg:p-8">
      <header className="flex flex-wrap items-center gap-4 rounded-card bg-card px-6 py-4 shadow-card">
        <ShieldMark />
        <div>
          <div className="text-3xl font-bold tracking-tight text-deep">Linda</div>
          <div className="text-base text-muted">Real numbers from real phones</div>
        </div>
        <div className="ml-auto flex items-center gap-4">
          {updated !== null && <span className="text-base text-muted">Updated {updated < 2 ? "just now" : `${updated} s ago`}</span>}
          {down ? <Pill tone="red">Can&apos;t reach the server</Pill> : stats ? <Pill>● LIVE</Pill> : <Pill tone="grey">Connecting…</Pill>}
        </div>
      </header>

      {down && (
        <div className="rounded-card bg-redTint px-6 py-4 text-lg text-red">
          The Linda server is not answering right now{stats ? ", so these are the last numbers it gave" : ""}. On the free
          server the first visit after a quiet spell can take about a minute to wake it.
        </div>
      )}

      <section className="grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
        <Tile label="Phones with Linda" value={stats?.phones.total}
          note={stats ? `${stats.phones.active_24h} active in the last 24 hours` : undefined} />
        <Tile label="Scams caught" value={stats?.scams_caught.total} tone="red"
          note={stats ? `${stats.scams_caught.last_24h} in the last 24 hours` : undefined} />
        <Tile label="Scam numbers confirmed" value={stats?.numbers.confirmed}
          note={stats ? `${stats.numbers.suspected} more suspected` : undefined} />
        <Tile label="App downloads" value={downloads ?? undefined} note="Of the newest build, from GitHub" />
      </section>

      <div className="grid gap-5 lg:grid-cols-2">
        <Card title="Scams by type">
          {!stats ? <Empty>Waiting for the server…</Empty>
            : bars(stats.by_category).length === 0 ? <Empty>No scams reported yet.</Empty>
            : (
              <ul className="space-y-3">
                {bars(stats.by_category).map((b) => (
                  <li key={b.category}>
                    <div className="mb-1 flex text-lg"><span className="text-ink">{categoryName(b.category)}</span>
                      <span className="ml-auto font-semibold tabular-nums text-ink">{b.count}</span></div>
                    <div className="h-3 rounded-full bg-raised">
                      <div className="h-3 rounded-full bg-green" style={{ width: `${b.width}%` }} />
                    </div>
                  </li>
                ))}
              </ul>
            )}
        </Card>

        <Card title="Latest scams caught">
          {!stats ? <Empty>Waiting for the server…</Empty>
            : stats.recent.length === 0 ? <Empty>No scams reported yet.</Empty>
            : (
              <ul className="divide-y divide-line">
                {stats.recent.map((r, i) => (
                  <li key={`${r.at}-${i}`} className="flex flex-wrap items-center gap-x-4 gap-y-1 py-3 text-lg">
                    <span className="font-semibold text-ink">{categoryName(r.category)}</span>
                    <span className="tabular-nums text-muted">{r.number}</span>
                    <span className="ml-auto flex items-center gap-3">
                      {STATUS[r.status] && <Pill tone={STATUS[r.status].tone}>{STATUS[r.status].text}</Pill>}
                      <span className="text-muted">{ago(r.at, serverNow)}</span>
                    </span>
                  </li>
                ))}
              </ul>
            )}
        </Card>
      </div>

      <p className="text-base text-muted">
        Counted only from phones that have reporting switched on, so the real number of scams Linda caught is higher.
        Numbers are partly hidden on purpose: this screen is public. No message text ever leaves a phone.
      </p>
    </main>
  );
}

function Tile({ label, value, note, tone = "green" }: { label: string; value?: number; note?: string; tone?: "green" | "red" }) {
  return (
    <div className="rounded-card bg-card p-6 shadow-card">
      <div className="text-lg text-muted">{label}</div>
      <div className={`mt-1 text-6xl font-bold tabular-nums tracking-tight ${tone === "red" ? "text-red" : "text-deep"}`}>
        {value === undefined ? "–" : value.toLocaleString("en-KE")}
      </div>
      {note && <div className="mt-2 text-base text-muted">{note}</div>}
    </div>
  );
}

function Empty({ children }: { children: React.ReactNode }) {
  return <p className="py-6 text-lg text-muted">{children}</p>;
}
