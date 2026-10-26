"use client";
// The presenter's buttons, in three plain groups, with hotkeys. Disabled in replay mode: there is no live simulator
// to command. Starting or resetting asks for the presenter password once (the browser remembers it).
import { useEffect } from "react";
import { runPreset } from "@/lib/api";
import { PRESETS, type Preset } from "@/lib/scenarios";

const GROUPS: { title: string; keys: string[] }[] = [
  { title: "Scam blast", keys: ["b"] },
  { title: "Adoption", keys: ["1", "2", "3"] },
  { title: "Calls", keys: ["c", "f"] },
  { title: "Tests", keys: ["o", "p"] },
];

/** Shorter words on the buttons, so they fit on one row; the group title says the rest. */
const SHORT: Record<string, string> = { "1": "0.5%", "2": "1%", "3": "2%", f: "Known number", o: "Rotating", p: "Poison test" };

export function Controls({ live }: { live: boolean }) {
  useEffect(() => {
    if (!live) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      const p = PRESETS.find((x) => x.key === e.key.toLowerCase());
      if (p) void runPreset(p);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [live]);

  const byKey = (k: string) => PRESETS.find((p) => p.key === k);
  const reset = byKey("r");
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-card bg-card px-5 py-3 shadow-card">
      {GROUPS.map((g) => (
        <div key={g.title} className="flex flex-wrap items-center gap-2">
          <span className="mr-1 text-xs font-semibold uppercase tracking-wide text-muted">{g.title}</span>
          {g.keys.map((k) => byKey(k)).filter((p): p is Preset => !!p).map((p) => (
            <PresetButton key={p.key} p={p} live={live} primary={p.key === "b"} />
          ))}
        </div>
      ))}
      {reset && <div className="ml-auto"><PresetButton p={reset} live={live} primary={false} /></div>}
    </div>
  );
}

function PresetButton({ p, live, primary }: { p: Preset; live: boolean; primary: boolean }) {
  const look = primary
    ? "bg-deep text-white hover:bg-forest"
    : "border border-muted bg-card text-ink hover:bg-raised";
  return (
    <button disabled={!live} onClick={() => void runPreset(p)}
      title={p.label} className={`rounded-2xl px-3.5 py-2 text-base font-semibold transition-transform duration-100 ease-out active:scale-[0.97] motion-reduce:transition-none motion-reduce:active:scale-100 disabled:cursor-not-allowed disabled:opacity-40 ${look}`}>
      {SHORT[p.key] ?? p.label}
      <span className={`ml-2 rounded px-1.5 text-xs ${primary ? "bg-white/20" : "bg-raised text-muted"}`}>{p.key.toUpperCase()}</span>
    </button>
  );
}
