"use client";
// Presenter controls with hotkeys. Disabled in replay mode: there is no live simulator to command.
import { useEffect } from "react";
import { resetSimulation, startScenario, type Scenario } from "@/lib/api";

const BUTTONS: { key: string; label: string; run: () => Promise<unknown>; color: string }[] = [
  { key: "r", label: "Reset", run: resetSimulation, color: "#6D7899" },
  { key: "b", label: "Start blast", run: () => startScenario("blast"), color: "#FF2E88" },
  { key: "o", label: "Rotating numbers", run: () => startScenario("rotating" as Scenario), color: "#FFC940" },
  { key: "p", label: "Poison test", run: () => startScenario("poison" as Scenario), color: "#00E5FF" },
];

export function Controls({ live }: { live: boolean }) {
  useEffect(() => {
    if (!live) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      const b = BUTTONS.find((x) => x.key === e.key.toLowerCase());
      if (b) void b.run();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [live]);

  return (
    <div className="flex flex-wrap gap-3">
      {BUTTONS.map((b) => (
        <button key={b.key} disabled={!live} onClick={() => void b.run()}
          className="rounded-2xl px-5 py-3 text-lg font-bold text-bg transition disabled:cursor-not-allowed disabled:opacity-30"
          style={{ background: b.color }}>
          {b.label} <span className="ml-1 rounded bg-black/20 px-1.5 text-sm">{b.key.toUpperCase()}</span>
        </button>
      ))}
    </div>
  );
}
