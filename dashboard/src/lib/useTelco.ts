"use client";
// Connects the dashboard to the simulator: live WebSocket with automatic reconnect, and the offline replay
// that takes over when the connection has been down for 5 seconds.
import { useCallback, useEffect, useRef, useState } from "react";
import { getMetrics, getPopulation } from "./api";
import { TELCO_WS } from "./config";
import fallbackJson from "./fallback/demo-events.json";
import { FALLBACK_AFTER_MS, REPLAY_PAUSE_BETWEEN_LOOPS_MS, REPLAY_SPEED, replayDelayMs } from "./replay";
import { initialState, metricsFromState, reduce, withPopulation, type DashboardState } from "./state";
import type { FallbackData, Metrics, SimEvent } from "./types";

const fallback = fallbackJson as unknown as FallbackData;

export type Mode = "connecting" | "live" | "replay";

export function useTelco() {
  const [state, setState] = useState<DashboardState>(() => initialState(fallback.population));
  const [mode, setMode] = useState<Mode>("connecting");
  const [liveMetrics, setLiveMetrics] = useState<Metrics | null>(null);
  const modeRef = useRef<Mode>("connecting");
  const setBoth = useCallback((m: Mode) => { modeRef.current = m; setMode(m); }, []);

  // ---- live feed -------------------------------------------------------------------------------
  useEffect(() => {
    let socket: WebSocket | null = null;
    let closed = false;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let downTimer: ReturnType<typeof setTimeout> | undefined;

    const armFallback = () => {
      if (downTimer) return;
      downTimer = setTimeout(() => { if (!closed && modeRef.current !== "live") setBoth("replay"); }, FALLBACK_AFTER_MS);
    };
    const disarmFallback = () => { if (downTimer) clearTimeout(downTimer); downTimer = undefined; };

    const connect = () => {
      if (closed) return;
      armFallback();
      try { socket = new WebSocket(TELCO_WS); } catch { retry = setTimeout(connect, 1000); return; }
      socket.onopen = () => {
        disarmFallback();
        setBoth("live");
        setState(initialState(fallback.population)); // the server now replays its history for us
        getPopulation().then((pop) => { if (pop && !closed) setState((s) => withPopulation(s, pop)); });
      };
      socket.onmessage = (m) => setState((s) => reduce(s, JSON.parse(m.data) as SimEvent));
      socket.onclose = () => {
        if (closed) return;
        if (modeRef.current === "live") setBoth("connecting");
        armFallback();
        retry = setTimeout(connect, 1000);
      };
      socket.onerror = () => socket?.close();
    };
    connect();
    return () => { closed = true; disarmFallback(); if (retry) clearTimeout(retry); socket?.close(); };
  }, [setBoth]);

  // ---- offline replay --------------------------------------------------------------------------
  useEffect(() => {
    if (mode !== "replay") return;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const events = fallback.events;
    const play = (i: number) => {
      if (cancelled) return;
      if (i >= events.length) {
        timer = setTimeout(() => { setState(initialState(fallback.population)); play(0); }, REPLAY_PAUSE_BETWEEN_LOOPS_MS);
        return;
      }
      timer = setTimeout(() => { if (cancelled) return; setState((s) => reduce(s, events[i])); play(i + 1); }, replayDelayMs(events, i, REPLAY_SPEED));
    };
    setState(initialState(fallback.population));
    play(0);
    return () => { cancelled = true; if (timer) clearTimeout(timer); };
  }, [mode]);

  // ---- headline numbers: the simulator's own /metrics when live, computed from the replay otherwise ----
  const sender = state.latestSender;
  useEffect(() => {
    if (mode !== "live") return;
    let stop = false;
    const tick = () => getMetrics(sender).then((m) => { if (!stop && m) setLiveMetrics(m); });
    tick();
    const id = setInterval(tick, 1200);
    return () => { stop = true; clearInterval(id); };
  }, [mode, sender]);

  const metrics: Metrics = mode === "live" && liveMetrics ? liveMetrics : metricsFromState(state, sender);
  return { state, mode, metrics };
}
