"""The virtual phone tour: installs Linda on an Android emulator and checks the main features like a person would.

It runs in GitHub Actions (.github/workflows/virtual-phone.yml), on an emulator that the workflow has already started.
It uses only adb and the Python standard library (helpers in phone.py):
  - `adb emu sms send` and `adb emu gsm call` make the emulator receive a REAL text and a REAL call, so Linda's actual
    SMS receiver and call screening run, not demo mode;
  - `uiautomator dump` lists what is on screen (Compose exposes its texts to it), so the tour finds buttons by their
    words and taps them, the way a tester would;
  - `dumpsys notification` shows which notifications Linda posted;
  - `screencap` saves a picture of every step.

Every check is recorded as pass or fail with a screenshot; one failing check does not stop the others. The report goes
to <out>/report.md and the script exits 1 if anything failed.

Run (with an emulator or a phone connected):
  python3 tools/virtual-phone/tour.py --apk android/app/build/outputs/apk/debug/app-debug.apk --out virtual-phone-out
"""
import argparse
import re
import sys
import time
from pathlib import Path

from phone import PKG, Phone
SCAMMER = "0700000777"   # fake placeholder number (DemoSamples.SCAMMER_NUMBER)
FRIEND = "0700000026"
FAKE_MPESA = ("QK7RT2XY9P Confirmed. You have received Ksh2,500.00 from JOHN KAMAU on 12/10/26 at 4:12 PM. "
              "New M-PESA balance is Ksh3,140.00.")
CHAT = "Hey, are we still meeting at 6?"
SCAM_TITLE = "Linda warning: this is likely a scam"
CALL_TITLE = "Likely scam caller"
VERIFIED_TITLE = "Verified: Safaricom Customer Care"


class Tour(Phone):
    """The checks. The adb helpers (tap, find, screenshot, ...) come from phone.py."""

    def __init__(self, out: Path):
        super().__init__(out)
        self.results: list[tuple[str, bool, str, str]] = []  # (check, passed, detail, screenshot)

    # ---------- recording ----------
    def check(self, name: str, passed: bool, detail: str = "", shot: str = "") -> None:
        if not shot:
            shot = self.screenshot(re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")[:40])
        self.results.append((name, passed, detail, shot))
        print(("PASS " if passed else "FAIL ") + name + (f": {detail}" if detail else ""), flush=True)

    def step(self, name: str, fn) -> None:
        """Run one check; a crash inside it is recorded as a failure, and the tour goes on."""
        try:
            fn()
        except Exception as e:  # noqa: BLE001 - the tour must keep going and report
            self.check(name, False, f"error: {e}")

    # ---------- the checks ----------
    def install(self, apk: str) -> None:
        ok, out, role = self.install_linda(apk)
        self.check("Install with permissions and call-screening role", ok and role,
                   f"install: {'Success' if ok else out.strip()[-200:]}; call-screening role: {role}")

    def welcome_and_home(self) -> None:
        self.open_app()
        welcome = self.find("Welcome to Linda", scrolls=0) is not None
        self.check("Welcome screen on first launch", welcome)
        tapped = self.tap_text("I am ready")
        time.sleep(1.5)
        home = self.wait_for(lambda: "LINDA is protecting you." in self.screen_text(), 10)
        self.check("Home says LINDA is protecting you", tapped and home,
                   "all permissions granted, so every layer should be on")

    def scam_sms(self) -> None:
        self.go_home()
        before = self.count(SCAM_TITLE)
        self.adb("emu", "sms", "send", SCAMMER, FAKE_MPESA)
        warned = self.wait_for(lambda: self.count(SCAM_TITLE) > before, 25)
        dump = self.notifications()
        reason = "did not come from M-Pesa" in dump
        hidden = "JOHN KAMAU" not in dump.split(SCAM_TITLE)[-1][:2000] if warned else False
        self.shell("cmd statusbar expand-notifications")
        time.sleep(2)
        shot = self.screenshot("sms-warning-in-notification-shade")
        self.check("Real SMS: fake M-Pesa from a personal number is warned", warned,
                   "notification posted by Linda's SMS receiver", shot)
        self.check("Warning explains why (reason on the notification)", reason, "'This looks like an M-Pesa message but did not come from M-Pesa'", shot)
        self.check("Message text is not shown on the notification", hidden, "", shot)
        buttons = all(b in self.screen_text() for b in ("See why", "Report this number"))
        self.check("Notification has See why and Report buttons", buttons, "", shot)

    def takeover_detail_report(self) -> None:
        # Still in the shade from the previous step: tap the warning itself.
        if not self.tap_text(SCAM_TITLE, scrolls=0):
            self.shell("cmd statusbar expand-notifications")
            time.sleep(1.5)
            self.tap_text(SCAM_TITLE, scrolls=0)
        takeover = self.wait_for(lambda: "Stop. This looks like a scam." in self.screen_text(), 10)
        self.check("Tapping the warning opens the full-screen stop", takeover)
        self.tap_text("OK, show me why")
        detail = self.wait_for(lambda: "Why LINDA flagged this" in self.screen_text() or self.find("Why LINDA flagged this") is not None, 8)
        self.check("Detail screen explains the warning", detail)
        # Rotating rebuilds the screen. It must stay on the detail, not replay the old warning (bug found 24 Oct).
        self.shell("settings put system accelerometer_rotation 0")
        self.shell("settings put system user_rotation 1")
        time.sleep(3)
        replayed = "Stop. This looks like a scam." in self.screen_text()
        self.check("Rotating the phone does not reopen the stop screen", not replayed)
        self.shell("settings put system user_rotation 0")
        time.sleep(2)
        opened = self.tap_text("Report this number")
        sheet = self.wait_for(lambda: "Report this number?" in self.screen_text(), 6)
        never = "Never sent: the message itself" in self.screen_text()
        self.check("Report sheet shows exactly what is sent, never the message", opened and sheet and never)
        self.tap_text("Cancel", scrolls=0)

    def safe_sms(self) -> None:
        self.go_home()
        self.shell("cmd statusbar collapse")
        before = self.count(SCAM_TITLE) + self.count("Linda: be careful")
        self.adb("emu", "sms", "send", FRIEND, CHAT)
        time.sleep(10)
        after = self.count(SCAM_TITLE) + self.count("Linda: be careful")
        self.check("Real SMS: an ordinary chat is not warned", after == before, f"Linda warnings before {before}, after {after}")

    def scam_call(self) -> None:
        self.go_home()
        before = self.count(CALL_TITLE)
        self.adb("emu", "gsm", "call", SCAMMER)
        labelled = self.wait_for(lambda: self.count(CALL_TITLE) > before, 20)
        shot = self.screenshot("incoming-call-from-scammer")
        self.check("Real call from the number that just sent a scam is labelled while ringing", labelled,
                   "heads-up 'Likely scam caller'", shot)
        self.adb("emu", "gsm", "cancel", SCAMMER)
        time.sleep(2)

    def tabs(self) -> None:
        self.open_app()
        self.tap_text("History", scrolls=0)
        hist = self.wait_for(lambda: any(s in self.screen_text() for s in ("0700000777", "0700 000 777", "254700000777")), 6)
        self.check("History lists the warned message", hist)
        self.tap_text("Check", scrolls=0)
        field = self.find("Paste the message here", scrolls=0)
        if field:
            self.shell(f"input tap {field['x']} {field['y']}")
            time.sleep(0.8)
            words = "Hongera!%sUmeshinda%sKsh%s50,000.%sTuma%sregistration%sfee%sya%sKsh%s500%supate%szawadi%syako."
            self.shell(f"input text '{words}'")
            self.shell("input keyevent KEYCODE_BACK")  # hide the keyboard
            time.sleep(0.8)
        clicked = self.tap_text("Check this message")
        verdict = self.wait_for(lambda: "Likely scam" in self.screen_text(), 8)  # the SCAM verdict label
        self.check("Check tab: a pasted prize scam gets a verdict", bool(field) and clicked and verdict)
        self.tap_text("Settings", scrolls=0)
        version = self.find("Linda version 0.1.")
        self.check("Settings shows the build number", version is not None, version["text"] if version else "")

    def verified_caller_demo(self) -> None:
        v = self.find("Linda version 0.1.")
        if not v:
            raise RuntimeError("version line not found")
        for _ in range(7):
            self.shell(f"input tap {v['x']} {v['y']}")
            time.sleep(0.25)
        time.sleep(1)
        opened = self.tap_text("Open demo mode")
        before = self.count(VERIFIED_TITLE)
        tapped = self.tap_text("Simulate a call from Safaricom Customer Care")
        badge = self.wait_for(lambda: self.count(VERIFIED_TITLE) > before, 10)
        self.shell("cmd statusbar expand-notifications")
        time.sleep(1.5)
        self.check("Verified caller badge for 0722 000 000 (demo mode)", opened and tapped and badge)
        self.shell("cmd statusbar collapse")

    def swahili(self) -> None:
        # On the emulator, Back presses after the simulated call never reach Demo mode (runs 5 and 6 stayed there),
        # so start the app afresh and open Settings from the bottom bar. The replay bug (an old warning coming back
        # when the screen is rebuilt) is still covered by the rotation check, and is checked again below.
        self.shell(f"am force-stop {PKG}")
        self.open_app()
        self.tap_exact("Settings", scrolls=0)
        time.sleep(1)
        # The language choices are at the top of Settings.
        switched = False
        for _ in range(5):
            if self.tap_exact("Kiswahili", scrolls=0):
                switched = True
                break
            self.swipe_down()
        time.sleep(3)  # the screen is rebuilt in the new language
        # Settings itself and the bottom bar must now be in Kiswahili ("Mipangilio", "Lugha", "Nyumbani").
        sw = self.wait_for(lambda: all(w in self.screen_text() for w in ("Lugha", "Nyumbani")), 8)
        stop = "Stop. This looks like a scam." in self.screen_text() or "Simama" in self.screen_text()
        self.check("Switching to Kiswahili changes the app's language", switched and sw and not stop,
                   "and does not reopen an old warning")

    # ---------- report ----------
    def report(self, apk: str) -> int:
        failed = [r for r in self.results if not r[1]]
        lines = ["# Virtual phone tour", "",
                 f"APK `{Path(apk).name}` on an Android emulator. {len(self.results) - len(failed)} of {len(self.results)} checks passed.", "",
                 "| | Check | Detail | Screenshot |", "|---|---|---|---|"]
        for name, ok, detail, shot in self.results:
            lines.append(f"| {'PASS' if ok else '**FAIL**'} | {name} | {detail.replace('|', '/')} | {shot} |")
        lines += ["", "An emulator is not a budget phone: it does not kill background apps, and installing with adb skips",
                  "Play Protect and Android's restricted settings. Do one real-phone run before the demo."]
        (self.out / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"\n{len(self.results) - len(failed)} of {len(self.results)} checks passed")
        return 1 if failed else 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--apk", required=True)
    ap.add_argument("--out", default="virtual-phone-out")
    args = ap.parse_args()
    t = Tour(Path(args.out))
    t.prepare()
    t.step("Install", lambda: t.install(args.apk))
    t.step("Welcome and Home", t.welcome_and_home)
    t.step("Scam SMS", t.scam_sms)
    t.step("Stop screen, detail and report", t.takeover_detail_report)
    t.step("Safe SMS", t.safe_sms)
    t.step("Scam call", t.scam_call)
    t.step("Tabs", t.tabs)
    t.step("Verified caller", t.verified_caller_demo)
    t.step("Kiswahili", t.swahili)
    return t.report(args.apk)


if __name__ == "__main__":
    sys.exit(main())
