"""Screen self-check: opens the live phone's browser screen through the public tunnel, the way a person would.

Optional step of .github/workflows/live-phone.yml (input "check_screen"). A headless Chrome on the runner opens
<url>/<key>/, takes a picture of the device list, clicks a player next to the phone, waits for the picture of the
phone, and saves both screenshots. It also checks that the address WITHOUT the key is refused.

Needs: pip install playwright (uses the runner's own Google Chrome, so no browser download).
Run:
  LIVE_PHONE_KEY=my-key python3 tools/virtual-phone/screen_check.py --url https://x.trycloudflare.com --out live-phone-out
"""
import argparse
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

from playwright.sync_api import sync_playwright

PLAYERS = ["WebCodecs", "Broadway.js", "H264 Converter", "Tiny H264"]


def status(url: str) -> int:
    try:
        with urllib.request.urlopen(url, timeout=30) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code


def main() -> int:
    ap = argparse.ArgumentParser(description="Live phone screen self-check")
    ap.add_argument("--url", required=True)
    ap.add_argument("--out", default="live-phone-out")
    args = ap.parse_args()
    key = os.environ["LIVE_PHONE_KEY"]
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    notes = [f"without the key: HTTP {status(args.url + '/')} (must be 404)"]

    with sync_playwright() as pw:
        try:  # the runner's Google Chrome (has the H.264 decoder); else Playwright's own Chromium
            browser = pw.chromium.launch(channel="chrome")
        except Exception:  # noqa: BLE001
            browser = pw.chromium.launch()
        page = browser.new_page(viewport={"width": 1100, "height": 900})
        page.goto(f"{args.url}/{key}/", wait_until="networkidle", timeout=60000)
        page.wait_for_selector(".device", timeout=60000)
        page.screenshot(path=out / "screen-check-1-list.png")
        notes.append("device list: shown")
        links = page.evaluate("() => [...document.querySelectorAll('a')].map(a => a.innerText + ' -> ' + a.href)")
        notes.append("links: " + "; ".join(l.split("#")[0] for l in links)[:600])
        opened, phone = "", page
        for player in PLAYERS:
            link = page.get_by_text(player, exact=True).first
            if link.count():
                with page.context.expect_page() as tab:  # ws-scrcpy opens the player in a new tab
                    link.click()
                phone, opened = tab.value, player
                break
        phone.wait_for_timeout(12000)  # the stream starts and the first frames arrive
        phone.screenshot(path=out / "screen-check-2-phone.png")
        drawn = phone.evaluate("() => document.querySelectorAll('canvas, video').length")
        notes.append(f"player: {opened or 'none found'}; canvas/video elements: {drawn}")
        browser.close()

    (out / "screen-check.txt").write_text("\n".join(notes) + "\n", encoding="utf-8")
    print("\n".join(notes))
    return 0


if __name__ == "__main__":
    sys.exit(main())
