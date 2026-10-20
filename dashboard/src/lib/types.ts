// The shapes the simulator sends (root CLAUDE.md contract 5.5, plus GET /population and GET /metrics).

export type PhoneKind = "linda" | "smart" | "feature";

export interface Phone {
  phone: number;
  kind: PhoneKind;
  town: string;
  lat: number;
  lon: number;
}

export type EventType =
  | "reset"
  | "blast_started"
  | "sms_delivered"
  | "linda_detected"
  | "report_sent"
  | "report_failed"
  | "sender_confirmed"
  | "warning_delivered"
  | "blast_finished"
  | "call_attempt"
  | "call_labelled";

export interface SimEvent {
  type: EventType;
  sim_time: number;
  run: number;
  sender?: string;
  phone?: number;
  kind?: PhoneKind;
  town?: string;
  lat?: number;
  lon?: number;
  before_read?: boolean;
  source?: "network" | "linda";
  category?: string;
  score?: number;
  level?: string;
  status?: string;
  devices?: number;
  scenario?: string;
  recipients?: number;
  confirmed_after_s?: number;
  attack?: boolean;
  poison_blocked?: boolean;
  channel?: "sms" | "after_call";      // warning_delivered: an SMS warning, or one sent right after a scam call
  real_sms?: boolean;                  // warning_delivered: went to a real phone on the demo table
  known?: boolean | string;            // call_attempt: the caller was confirmed by then
  label?: "reported" | "recent_scam_message" | "suspected";  // call_labelled
  calls?: number;
  by?: string;                         // sender_confirmed: "gateway" when it was already known
}

export interface CallMetrics {
  placed: number;
  to_linda_phones: number;
  labelled_before_answer: number;
  to_other_phones: number;
  already_warned_by_sms: number;
  warned_after_call: number;
  unprotected: number;
  protected_pct: number | null;
}

export interface Metrics {
  sender: string | null;
  run: number;
  delivered: number;
  warned: number;
  warned_before_read_pct: number | null;
  feature_phones_delivered: number;
  feature_phones_warned_before_read_pct: number | null;
  first_detection_after_s: number | null;
  confirmed_after_s: number | null;
  warned_by_linda: number;
  warned_by_network: number;
  adoption_pct?: number | null;
  linda_recipients?: number;
  calls?: CallMetrics | null;
}

export interface FallbackData {
  population: Phone[];
  events: SimEvent[];
  metrics: Metrics;
}
