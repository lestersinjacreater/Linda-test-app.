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
