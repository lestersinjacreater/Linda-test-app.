import { describe, expect, it } from "vitest";
import fallback from "./fallback/demo-events.json";
import { describe as say, initialState, metricsFromState, reduce, withPopulation } from "./state";
import { FALLBACK_AFTER_MS, replayDelayMs } from "./replay";
import type { FallbackData, SimEvent } from "./types";

const data = fallback as unknown as FallbackData;
const ev = (type: SimEvent["type"], extra: Partial<SimEvent> = {}): SimEvent => ({ type, sim_time: 0, run: 1, ...extra });
const phones = [
  { phone: 1, kind: "feature" as const, town: "Nairobi", lat: -1.3, lon: 36.8 },
  { phone: 2, kind: "linda" as const, town: "Kisumu", lat: -0.1, lon: 34.7 },
];

describe("phone status", () => {
  it("starts grey and walks received -> detected -> warned", () => {
    let s = initialState(phones);
    expect(s.phones[1].status).toBe("idle");
    s = reduce(s, ev("sms_delivered", { phone: 1 }));
    expect(s.phones[1].status).toBe("received");
    s = reduce(s, ev("linda_detected", { phone: 2, kind: "linda" }));
    expect(s.phones[2].status).toBe("detected");
    s = reduce(s, ev("warning_delivered", { phone: 1, before_read: true }));
    expect(s.phones[1].status).toBe("warned");
    expect(s.phones[1].warnedBeforeRead).toBe(true);
  });

  it("never goes backwards when events arrive out of order", () => {
    let s = initialState(phones);
    s = reduce(s, ev("warning_delivered", { phone: 1, before_read: true }));
    s = reduce(s, ev("sms_delivered", { phone: 1 }));
    expect(s.phones[1].status).toBe("warned");
  });

  it("reset clears the activity but keeps the phones on the map", () => {
    let s = reduce(initialState(phones), ev("sms_delivered", { phone: 1 }));
    s = reduce(s, ev("reset"));
    expect(s.phones[1].status).toBe("idle");
    expect(Object.keys(s.phones)).toHaveLength(2);
    expect(s.feed).toHaveLength(0);
  });
});

describe("population and latest sender", () => {
  it("adds missing phones without disturbing known ones", () => {
    let s = reduce(initialState(phones), ev("sms_delivered", { phone: 1 }));
    s = withPopulation(s, [...phones, { phone: 3, kind: "smart", town: "Thika", lat: -1, lon: 37 }]);
    expect(s.phones[1].status).toBe("received");
    expect(s.phones[3].status).toBe("idle");
  });

  it("tracks the latest real blast but not attacks", () => {
    let s = reduce(initialState(phones), ev("blast_started", { sender: "254700900010" }));
    s = reduce(s, ev("blast_started", { sender: "254700900020", attack: true }));
    expect(s.latestSender).toBe("254700900010");
  });
});

describe("campaigns and the attack panel", () => {
  it("lists a confirmed sender with its category and report count", () => {
    let s = initialState(phones);
    s = reduce(s, ev("linda_detected", { phone: 2, sender: "254700900010", category: "fake_mpesa" }));
    s = reduce(s, ev("report_sent", { sender: "254700900010", status: "unknown", devices: 1 }));
    s = reduce(s, ev("report_sent", { sender: "254700900010", status: "suspected", devices: 2 }));
    s = reduce(s, ev("sender_confirmed", { sender: "254700900010", confirmed_after_s: 28.5 }));
    expect(s.campaigns["254700900010"]).toEqual({ sender: "254700900010", category: "fake_mpesa", reports: 2, confirmedAfterS: 28.5 });
  });

  it("does not list an attacked innocent number as a campaign, and shows the attack was blocked", () => {
    let s = initialState(phones);
    s = reduce(s, ev("blast_started", { sender: "254700900020", attack: true }));
    s = reduce(s, ev("report_sent", { sender: "254700900020", status: "unknown", devices: 3, attack: true }));
    s = reduce(s, ev("blast_finished", { sender: "254700900020", attack: true, poison_blocked: true }));
    expect(Object.keys(s.campaigns)).toHaveLength(0);
    expect(s.poison).toEqual({ attempted: true, blocked: true });
  });

  it("trims the live feed", () => {
    let s = initialState(phones);
    for (let i = 0; i < 300; i++) s = reduce(s, ev("sms_delivered", { phone: 1, sim_time: i }));
    expect(s.feed.length).toBeLessThanOrEqual(120);
    expect(s.feed[0].sim_time).toBe(299);
  });
});

describe("the recorded fallback run", () => {
  it("replays into the same headline numbers the simulator reported", () => {
    let s = initialState(data.population);
    for (const e of data.events) s = reduce(s, e);
    const m = metricsFromState(s, data.metrics.sender);
    expect(m.delivered).toBe(data.metrics.delivered);
    expect(m.warned).toBe(data.metrics.warned);
    expect(m.warned_before_read_pct).toBe(data.metrics.warned_before_read_pct);
    expect(m.feature_phones_warned_before_read_pct).toBe(data.metrics.feature_phones_warned_before_read_pct);
    expect(m.confirmed_after_s).toBe(data.metrics.confirmed_after_s);
    expect(m.first_detection_after_s).toBe(data.metrics.first_detection_after_s);
  });

  it("is a full story: grey phones turn amber, red and green, and the sender is confirmed", () => {
    const types = new Set(data.events.map((e) => e.type));
    for (const t of ["blast_started", "sms_delivered", "linda_detected", "report_sent", "sender_confirmed", "warning_delivered", "blast_finished"]) {
      expect(types.has(t as SimEvent["type"])).toBe(true);
    }
    expect(data.population.length).toBe(300);
  });

  it("never contains message text", () => {
    const keys = new Set(data.events.flatMap((e) => Object.keys(e)));
    for (const forbidden of ["text", "body", "message"]) expect(keys.has(forbidden)).toBe(false);
  });
});

describe("replay timing and wording", () => {
  it("waits real time proportional to simulated gaps, capped at one second", () => {
    const events = [ev("reset", { sim_time: 0 }), ev("reset", { sim_time: 10 }), ev("reset", { sim_time: 500 })];
    expect(replayDelayMs(events, 0, 20)).toBe(0);
    expect(replayDelayMs(events, 1, 20)).toBe(500);
    expect(replayDelayMs(events, 2, 20)).toBe(1000);
    expect(FALLBACK_AFTER_MS).toBe(5000);
  });

  it("describes events in plain English without any message text", () => {
    expect(say(ev("sender_confirmed", { sender: "254700900010", confirmed_after_s: 28.5 }))).toContain("RADAR CONFIRMED");
    expect(say(ev("warning_delivered", { kind: "feature", town: "Garissa", before_read: true }))).toBe("Basic phone in Garissa warned by the network before reading");
  });
});
