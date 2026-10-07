// The offline replay: when the live connection is down, play back a recorded run so the stage is never blank.
import type { SimEvent } from "./types";

/** Real milliseconds to wait before showing event `i` after event `i-1`. `speed` = simulated seconds per real second. */
export function replayDelayMs(events: SimEvent[], i: number, speed: number): number {
  if (i <= 0) return 0;
  const gap = Math.max(0, events[i].sim_time - events[i - 1].sim_time);
  return Math.min(1000, (gap / speed) * 1000); // never stall more than a second on one gap
}

/** How long the live connection may be down before the replay takes over (spec: 5 seconds). */
export const FALLBACK_AFTER_MS = 5000;
export const REPLAY_SPEED = 20;
export const REPLAY_PAUSE_BETWEEN_LOOPS_MS = 4000;
