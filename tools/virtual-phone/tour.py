"""The virtual phone tour: installs Linda on an Android emulator and checks the main features like a person would.

It runs in GitHub Actions (.github/workflows/virtual-phone.yml), on an emulator that the workflow has already started.
It uses only adb and the Python standard library:
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
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PKG = "com.linda.app"
SCAMMER = "0700000777"   # fake placeholder number (DemoSamples.SCAMMER_NUMBER)
FRIEND = "0700000026"
FAKE_MPESA = ("QK7RT2XY9P Confirmed. You have received Ksh2,500.00 from JOHN KAMAU on 12/10/26 at 4:12 PM. "
              "New M-PESA balance is Ksh3,140.00.")
CHAT = "Hey, are we still meeting at 6?"
SCAM_TITLE = "Linda warning: this is likely a scam"
CALL_TITLE = "Likely scam caller"
VERIFIED_TITLE = "Verified: Safaricom Customer Care"


class Tour:
    def __init__(self, out: Path):
        self.out = out
        self.out.mkdir(parents=True, exist_ok=True)
        self.results: list[tuple[str, bool, str, str]] = []  # (check, passed, detail, screenshot)
        self.shots = 0

    # ---------- adb helpers ----------
    def adb(self, *args: str, check: bool = False, timeout: int = 60) -> str:
        p = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
        if check and p.returncode != 0:
            raise RuntimeError(f"adb {' '.join(args)} failed: {p.stderr.strip() or p.stdout.strip()}")
        return (p.stdout or "") + (p.stderr or "")

    def shell(self, cmd: str, timeout: int = 60) -> str:
        return self.adb("shell", cmd, timeout=timeout)

    def screenshot(self, name: str) -> str:
        self.shots += 1
        fname = f"{self.shots:02d}-{name}.png"
        with open(self.out / fname, "wb") as f:
            f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)
        return fname

    def screen_nodes(self) -> list[dict]:
        """Every on-screen element with text or a description, and its centre."""
        self.shell("uiautomator dump /sdcard/ui.xml")
        xml = self.adb("exec-out", "cat", "/sdcard/ui.xml")
        start = xml.find("<?xml")
        if start < 0:
            return []
        try:
            root = ET.fromstring(xml[start:xml.rfind(">") + 1])
        except ET.ParseError:
            return []
        nodes = []
        for n in root.iter("node"):
            text = (n.get("text") or "") + " " + (n.get("content-desc") or "")
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
            if text.strip() and m:
                x1, y1, x2, y2 = map(int, m.groups())
                nodes.append({"text": text.strip(), "x": (x1 + x2) // 2, "y": (y1 + y2) // 2})
        return nodes

    def screen_text(self) -> str:
        return "\n".join(n["text"] for n in self.screen_nodes())

    def find(self, words: str, scrolls: int = 4) -> dict | None:
        """Find an element containing these words, scrolling down a few times if needed."""
        for i in range(scrolls + 1):
            for n in self.screen_nodes():
                if words.lower() in n["text"].lower():
                    return n
            if i < scrolls:
                self.swipe_up()
        return None

    def tap_text(self, words: str, scrolls: int = 4) -> bool:
        n = self.find(words, scrolls)
        if n is None:
            return False
        self.shell(f"input tap {n['x']} {n['y']}")
        time.sleep(1.5)
        return True

    def swipe_up(self) -> None:
        w, h = self.size()
        self.shell(f"input swipe {w // 2} {int(h * 0.75)} {w // 2} {int(h * 0.30)} 300")
        time.sleep(0.8)

    def size(self) -> tuple[int, int]:
        m = re.search(r"(\d+)x(\d+)", self.shell("wm size"))
        return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)

    def notifications(self) -> str:
        return self.shell("dumpsys notification --noredact")

    def count(self, title: str) -> int:
        return self.notifications().count(title)

    def wait_for(self, fn, seconds: float = 20, every: float = 1.0) -> bool:
        end = time.time() + seconds
        while time.time() < end:
            if fn():
                return True
            time.sleep(every)
        return False

    def open_app(self) -> None:
        self.shell(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1")
        time.sleep(3)

    def go_home(self) -> None:
        self.shell("input keyevent KEYCODE_HOME")
        time.sleep(1)

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
        self.adb("uninstall", PKG)
        out = self.adb("install", "-r", "-g", apk, timeout=180)  # -g grants SMS, contacts and notifications
        ok = "Success" in out
        # Linda labels calls only if it holds the call-screening role; a person grants it in Settings.
        self.shell(f"cmd role add-role-holder android.app.role.CALL_SCREENING {PKG}")
        self.shell(f"dumpsys deviceidle whitelist +{PKG}")  # like the battery step in the welcome screen
        role = PKG in self.shell("cmd role get-role-holders android.app.role.CALL_SCREENING")
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
        self.shell("input keyevent KEYCODE_BACK")
        time.sleep(1)
        self.tap_text("Settings", scrolls=0)
        switched = self.tap_text("Kiswahili")
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
    t.adb("wait-for-device", timeout=300)
    t.wait_for(lambda: t.shell("getprop sys.boot_completed").strip() == "1", 300, 3)
    t.shell("settings put global window_animation_scale 0")
    t.shell("input keyevent KEYCODE_WAKEUP")
    t.shell("wm dismiss-keyguard")
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
