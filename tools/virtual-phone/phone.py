"""Phone: small helpers that drive an Android emulator (or a phone) over adb, used by the tour and the live phone.

Only adb and the Python standard library:
  - `adb emu sms send` / `adb emu gsm call` make the emulator receive a REAL text and a REAL call;
  - `uiautomator dump` lists what is on screen (Compose exposes its texts), so buttons are found by their words;
  - `dumpsys notification` shows which notifications were posted;
  - `screencap` saves a picture of the screen.
"""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PKG = "com.linda.app"


def input_text_arg(text: str) -> str:
    """Quote text for `adb shell input text`: spaces become %s, and the whole thing is single-quoted for the shell."""
    return "'" + text.replace("'", "'\\''").replace(" ", "%s") + "'"


class Phone:
    def __init__(self, out: Path):
        self.out = out
        self.out.mkdir(parents=True, exist_ok=True)
        self.shots = 0

    # ---------- adb ----------
    def adb(self, *args: str, check: bool = False, timeout: int = 60) -> str:
        p = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
        if check and p.returncode != 0:
            raise RuntimeError(f"adb {' '.join(args)} failed: {p.stderr.strip() or p.stdout.strip()}")
        return (p.stdout or "") + (p.stderr or "")

    def shell(self, cmd: str, timeout: int = 60) -> str:
        return self.adb("shell", cmd, timeout=timeout)

    def prepare(self) -> None:
        """Wait for the emulator to finish booting, then wake and unlock it."""
        self.adb("wait-for-device", timeout=300)
        self.wait_for(lambda: self.shell("getprop sys.boot_completed").strip() == "1", 300, 3)
        self.shell("settings put global window_animation_scale 0")
        self.shell("input keyevent KEYCODE_WAKEUP")
        self.shell("wm dismiss-keyguard")

    def install_linda(self, apk: str) -> tuple[bool, str, bool]:
        """Fresh install with every permission granted, plus the call-screening role. Returns (ok, adb output, role)."""
        self.adb("uninstall", PKG)
        out = self.adb("install", "-r", "-g", apk, timeout=180)  # -g grants SMS, contacts and notifications
        ok = "Success" in out
        # Linda labels calls only if it holds the call-screening role; a person grants it in Settings.
        self.shell(f"cmd role add-role-holder android.app.role.CALL_SCREENING {PKG}")
        self.shell(f"dumpsys deviceidle whitelist +{PKG}")  # like the battery step in the welcome screen
        # The role is granted in the background, so give it a few seconds to show up.
        role = self.wait_for(
            lambda: PKG in self.shell("cmd role get-role-holders android.app.role.CALL_SCREENING"), 10)
        return ok, out, role

    # ---------- the screen ----------
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

    def tap_exact(self, words: str, scrolls: int = 4) -> bool:
        """Tap an element whose whole text is exactly these words (so "Kiswahili" never hits "Prize scam (Kiswahili)")."""
        for i in range(scrolls + 1):
            for n in self.screen_nodes():
                if n["text"] == words:
                    self.shell(f"input tap {n['x']} {n['y']}")
                    time.sleep(1.5)
                    return True
            if i < scrolls:
                self.swipe_up()
        return False

    def tap_text(self, words: str, scrolls: int = 4) -> bool:
        n = self.find(words, scrolls)
        if n is None:
            return False
        self.shell(f"input tap {n['x']} {n['y']}")
        time.sleep(1.5)
        return True

    def type_text(self, text: str) -> None:
        """Type into whatever field has focus (plain letters, digits and punctuation; no emoji)."""
        self.shell(f"input text {input_text_arg(text)}")

    def swipe_up(self) -> None:
        """Scroll the page down (finger moves up)."""
        w, h = self.size()
        self.shell(f"input swipe {w // 2} {int(h * 0.75)} {w // 2} {int(h * 0.30)} 300")
        time.sleep(0.8)

    def swipe_down(self) -> None:
        """Scroll back towards the top of the screen."""
        w, h = self.size()
        self.shell(f"input swipe {w // 2} {int(h * 0.30)} {w // 2} {int(h * 0.75)} 300")
        time.sleep(0.8)

    def size(self) -> tuple[int, int]:
        m = re.search(r"(\d+)x(\d+)", self.shell("wm size"))
        return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)

    # ---------- notifications and apps ----------
    def notifications(self) -> str:
        return self.shell("dumpsys notification --noredact")

    def count(self, title: str) -> int:
        return self.notifications().count(title)

    def linda_notifications(self) -> list[str]:
        """Title and text of every notification Linda is showing now, newest first."""
        found = []
        for block in self.notifications().split("NotificationRecord(")[1:]:
            if f"pkg={PKG}" not in block[:300]:
                continue
            title = re.search(r"android\.title=String \((.*?)\)\n", block)
            text = re.search(r"android\.text=String \((.*?)\)\n", block)
            if title:
                found.append(title.group(1) + (f": {text.group(1)}" if text else ""))
        return found

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
