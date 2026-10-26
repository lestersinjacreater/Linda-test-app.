import { describe, expect, it } from "vitest";
import { telcoWs } from "./config";

describe("telcoWs", () => {
  it("turns an absolute API address into the live-feed address", () => {
    expect(telcoWs("http://localhost:8000", undefined, "")).toBe("ws://localhost:8000/events");
    expect(telcoWs("https://linda.example.com/telco", undefined, "")).toBe("wss://linda.example.com/telco/events");
  });

  it("resolves a relative address against the page (one server behind Caddy, e.g. Render)", () => {
    expect(telcoWs("/telco", "https://linda-x.onrender.com/", "")).toBe("wss://linda-x.onrender.com/telco/events");
    expect(telcoWs("/telco", "http://192.168.1.20/?x=1", "")).toBe("ws://192.168.1.20/telco/events");
  });

  it("an explicit live-feed address wins", () => {
    expect(telcoWs("/telco", "https://a.example/", "wss://b.example/feed")).toBe("wss://b.example/feed");
  });
});
