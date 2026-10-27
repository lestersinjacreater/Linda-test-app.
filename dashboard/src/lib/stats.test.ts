import { describe, expect, it } from "vitest";
import { ago, bars, downloadsOf } from "./stats";

describe("real-number helpers", () => {
  it("says how long ago in plain words", () => {
    expect(ago(1000, 1030)).toBe("just now");
    expect(ago(1000, 1000 + 4 * 60)).toBe("4 min ago");
    expect(ago(1000, 1000 + 3 * 3600)).toBe("3 h ago");
    expect(ago(1000, 1000 + 86400)).toBe("1 day ago");
    expect(ago(1000, 1000 + 5 * 86400)).toBe("5 days ago");
    expect(ago(2000, 1000)).toBe("just now"); // clocks a little apart never give "-5 s"
  });

  it("sorts bars biggest first, the biggest filling the row", () => {
    expect(bars({ prize: 2, fake_mpesa: 8 })).toEqual([
      { category: "fake_mpesa", count: 8, width: 100 },
      { category: "prize", count: 2, width: 25 },
    ]);
    expect(bars({})).toEqual([]);
  });

  it("counts only linda.apk downloads", () => {
    const release = { assets: [{ name: "linda-debug.apk", download_count: 9 }, { name: "linda.apk", download_count: 4 }] };
    expect(downloadsOf(release)).toBe(4);
    expect(downloadsOf({})).toBe(0);
  });
});

import { feedLine, isWaiting } from "./fraudteam";

describe("fraud-team console helpers", () => {
  it("says what happened in plain words", () => {
    const name = (c: string) => (c === "prize" ? "Prize scam" : c);
    expect(feedLine({ at: 0, kind: "report", number: "0700 000 777", category: "prize" }, name))
      .toBe("0700 000 777 reported by a customer: Prize scam.");
    expect(feedLine({ at: 0, kind: "confirmed", number: "0700 000 777", category: "prize" }, name))
      .toContain("Sent to every Linda phone");
  });

  it("waits only on numbers nobody has decided yet", () => {
    const row = (status: "reported" | "suspected" | "confirmed" | "allowlisted") =>
      ({ msisdn: "", number: "", category: "", reports: 1, phones: 1, first_at: 0, last_at: 0, status, new: false });
    expect(["reported", "suspected", "confirmed", "allowlisted"].map((s) => isWaiting(row(s as never))))
      .toEqual([true, true, false, false]);
  });
});
