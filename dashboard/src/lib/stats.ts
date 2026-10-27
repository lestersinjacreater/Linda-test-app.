// Real numbers for the main page: the gateway's GET /v1/stats (contract 5.14) and GitHub's download counter.
// Nothing here is simulated or made up. When a source cannot be reached the page says so instead of guessing.

/** The gateway. "/radar" = the same server this page came from (Caddy on Render; next.config.mjs locally). */
export const RADAR_API = process.env.NEXT_PUBLIC_RADAR_API || "/radar";
/** The public release the app updates from (Settings → Updates uses the same one). */
export const GITHUB_REPO = process.env.NEXT_PUBLIC_GITHUB_REPO || "lestersinjacreater/linda-test-app.";

export type Stats = {
  as_of: number;
  phones: { total: number; active_24h: number };
  scams_caught: { total: number; last_24h: number };
  numbers: { confirmed: number; suspected: number };
  by_category: Record<string, number>;
  recent: { category: string; at: number; number: string; status: string }[];
};

export async function fetchStats(api: string = RADAR_API): Promise<Stats> {
  const r = await fetch(`${api.replace(/\/$/, "")}/v1/stats`, { cache: "no-store" });
  if (!r.ok) throw new Error(`gateway answered ${r.status}`);
  return r.json();
}

/**
 * Downloads of linda.apk from the latest-build release. GitHub replaces that release on every new build, so this
 * counts downloads of the CURRENT build only (in-app updates included). GitHub allows 60 unsigned requests an hour
 * per network, so the page asks every few minutes, not every 10 seconds.
 */
export async function fetchDownloads(repo: string = GITHUB_REPO): Promise<number> {
  const r = await fetch(`https://api.github.com/repos/${repo}/releases/tags/latest-build`, {
    headers: { Accept: "application/vnd.github+json" },
  });
  if (!r.ok) throw new Error(`GitHub answered ${r.status}`);
  return downloadsOf(await r.json());
}

export function downloadsOf(release: { assets?: { name: string; download_count: number }[] }): number {
  return release.assets?.find((a) => a.name === "linda.apk")?.download_count ?? 0;
}

/** "just now", "4 min ago", "3 h ago", "2 days ago". `then` and `now` in seconds. */
export function ago(then: number, now: number): string {
  const s = Math.max(0, Math.round(now - then));
  if (s < 60) return "just now";
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  const d = Math.floor(s / 86400);
  return d === 1 ? "1 day ago" : `${d} days ago`;
}

/** Bars for "Scams by type", biggest first, each as a share of the biggest (so the longest bar fills the row). */
export function bars(byCategory: Record<string, number>): { category: string; count: number; width: number }[] {
  const rows = Object.entries(byCategory).sort((a, b) => b[1] - a[1]);
  const top = rows[0]?.[1] ?? 0;
  return rows.map(([category, count]) => ({ category, count, width: top ? Math.max(4, Math.round((count / top) * 100)) : 0 }));
}
