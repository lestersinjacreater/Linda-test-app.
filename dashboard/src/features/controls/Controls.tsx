"use client";
// Presenter controls with hotkeys. Disabled in replay mode: there is no live simulator to command.
import { useEffect } from "react";
import { runPreset } from "@/lib/api";
import { PRESETS } from "@/lib/scenarios";

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

  return (
    <div className="flex flex-wrap gap-2">
      {PRESETS.map((p) => (
        <button key={p.key} disabled={!live} onClick={() => void runPreset(p)}
          className="rounded-2xl px-4 py-2.5 text-base font-bold text-bg transition-transform duration-100 ease-out active:scale-[0.97] motion-reduce:transition-none motion-reduce:active:scale-100 disabled:cursor-not-allowed disabled:opacity-30"
          style={{ background: p.color }}>
          {p.label} <span className="ml-1 rounded bg-black/20 px-1.5 text-sm">{p.key.toUpperCase()}</span>
        </button>
      ))}
    </div>
  );
}
