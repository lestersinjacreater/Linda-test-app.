"""The live phone's command listener: obeys /phone commands posted on the repo's "Live phone" issue.

Runs in GitHub Actions (.github/workflows/live-phone.yml) next to a running emulator. Every few seconds it reads new
comments on the issue. A comment from the repo owner or a collaborator can hold one or more lines that start with
/phone; the listener runs them in order on the emulator, then replies with what happened, a screenshot and the text on
screen. Comments from anyone else are ignored, because the repo is public.

Only the Python standard library. The GitHub token comes from the GITHUB_TOKEN environment variable.

Run (inside the workflow; needs adb and an emulator):
  python3 tools/virtual-phone/listen.py --apk app-debug.apk --minutes 60 --screen-url https://x.trycloudflare.com
"""
import argparse
import json
import os
import re
import sys
import time
import traceback
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

from phone import Phone

ISSUE_TITLE = "Live phone"
SESSION_TAG = "live-phone-session"   # pre-release that holds this session's screenshots
TRUSTED = {"OWNER", "MEMBER", "COLLABORATOR"}
NAIROBI = timezone(timedelta(hours=3))
POLL_S = 5
MAX_COMMANDS = 20

HELP = """\
| Command | What it does |
|---|---|
| `/phone shot` | Screenshot and the text on screen |
| `/phone sms 0700000777 Your text` | A real SMS arrives from that number |
| `/phone call 0700000777` | A real incoming call (`/phone hangup` ends it) |
| `/phone tap Settings` | Tap the thing with these words |
| `/phone tapxy 540 1200` | Tap a point on the screen |
| `/phone type some words` | Type into the field that has focus |
| `/phone back` · `/phone home` · `/phone enter` | Phone keys |
| `/phone scroll down` · `/phone scroll up` | Scroll the page |
| `/phone rotate` | Turn the phone sideways and back |
| `/phone open` | Open Linda |
| `/phone notifications` | List Linda's notifications |
| `/phone install` | Reinstall this session's build (clears Linda's data) |
| `/phone install latest` | Install the newest build from the latest-build release |
| `/phone wait 10` | Wait (up to 30 s) before the next command |
| `/phone stop` | End the session now |

Several commands in one comment run in order, one per line. Use fake numbers only (`0700000xxx`): this issue is public."""


# ---------- the parts that need no phone and no network (unit-tested) ----------
def parse_commands(body: str) -> list[tuple[str, str]]:
    """Every line that starts with /phone becomes (command, arguments), in order."""
    found = []
    for line in (body or "").splitlines():
        line = line.strip()
        if not line.lower().startswith("/phone"):
            continue
        rest = line[len("/phone"):]
        if rest and not rest[0].isspace():
            continue  # "/phonecall" is not a command
        cmd, _, args = rest.strip().partition(" ")
        found.append((cmd.lower() or "help", args.strip()))
    return found[:MAX_COMMANDS]


def is_trusted(comment: dict) -> bool:
    """Only people who can already change the repo may drive the phone; bots (including our own replies) never."""
    user = comment.get("user") or {}
    return user.get("type") != "Bot" and comment.get("author_association") in TRUSTED


def valid_number(text: str) -> bool:
    return re.fullmatch(r"\+?\d{3,15}", text or "") is not None


# ---------- GitHub ----------
class GitHub:
    def __init__(self, repo: str, token: str, api: str = "https://api.github.com"):
        self.repo, self.token, self.api = repo, token, api  # api can point at a fake GitHub in tests
        self.etags: dict[str, tuple[str, object]] = {}

    def call(self, method: str, url: str, body=None, raw: bytes | None = None, ctype: str = "application/json",
             cache: bool = False):
        if url.startswith("/"):
            url = f"{self.api}/repos/{self.repo}{url}"
        data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Authorization", f"Bearer {self.token}")
        req.add_header("Accept", "application/vnd.github+json")
        if data is not None:
            req.add_header("Content-Type", ctype)
        if cache and url in self.etags:
            req.add_header("If-None-Match", self.etags[url][0])  # a 304 answer does not use up the rate limit
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                text = r.read().decode() or "null"
                result = json.loads(text)
                if cache and r.headers.get("ETag"):
                    self.etags[url] = (r.headers["ETag"], result)
                return result
        except urllib.error.HTTPError as e:
            if e.code == 304 and url in self.etags:
                return self.etags[url][1]
            if e.code == 404 and method in ("GET", "DELETE"):
                return None
            if e.code == 422 and method == "DELETE":  # deleting a tag that is already gone
                return None
            raise RuntimeError(f"GitHub {method} {url}: {e.code} {e.read().decode()[:300]}") from e

    def issue(self) -> int:
        for i in self.call("GET", "/issues?state=open&per_page=100") or []:
            if i.get("title") == ISSUE_TITLE and "pull_request" not in i:
                return i["number"]
        made = self.call("POST", "/issues", {"title": ISSUE_TITLE, "body":
                         "Commands for the live phone (Actions → Live phone → Run workflow). Each session posts its "
                         "link and the command list here.\n\n" + HELP})
        return made["number"]

    def comment(self, issue: int, body: str) -> None:
        self.call("POST", f"/issues/{issue}/comments", {"body": body})

    def new_comments(self, issue: int, since: str) -> list[dict]:
        return self.call("GET", f"/issues/{issue}/comments?since={since}&per_page=100", cache=True) or []

    def fresh_release(self, commit: str) -> dict:
        old = self.call("GET", f"/releases/tags/{SESSION_TAG}")
        if old:
            self.call("DELETE", f"/releases/{old['id']}")
        self.call("DELETE", f"/git/refs/tags/{SESSION_TAG}")
        return self.call("POST", "/releases", {"tag_name": SESSION_TAG, "target_commitish": commit,
                         "name": "Live phone session (screenshots)", "prerelease": True,
                         "body": "Screenshots from the latest live phone session. See the Live phone issue."})

    def upload(self, release: dict, path: Path) -> str:
        url = f"https://uploads.github.com/repos/{self.repo}/releases/{release['id']}/assets?name={path.name}"
        return self.call("POST", url, raw=path.read_bytes(), ctype="image/png")["browser_download_url"]

    def latest_apk(self, dest: Path) -> None:
        rel = self.call("GET", "/releases/tags/latest-build")
        asset = next((a for a in (rel or {}).get("assets", []) if a["name"] == "linda-debug.apk"), None)
        if not asset:
            raise RuntimeError("no linda-debug.apk on the latest-build release")
        with urllib.request.urlopen(asset["browser_download_url"], timeout=180) as r:  # public repo: no token needed
            dest.write_bytes(r.read())


# ---------- the commands ----------
class Session:
    def __init__(self, phone: Phone, gh: GitHub, apk: str):
        self.phone, self.gh, self.apk = phone, gh, apk
        self.ringing: set[str] = set()
        self.landscape = False
        self.stopped = False

    def run(self, cmd: str, args: str) -> str:
        """Run one command; returns a short note for the reply. Raises ValueError for a wrong command."""
        p = self.phone
        if cmd == "help":
            return "Command list below."
        if cmd in ("shot", "screen"):
            return "Screenshot below."
        if cmd == "sms":
            number, _, text = args.partition(" ")
            text = " ".join(text.split())  # one line: the emulator console takes a single line
            if not valid_number(number) or not text or len(text) > 600:
                raise ValueError("use: /phone sms 0700000777 Your message (up to 600 characters)")
            p.adb("emu", "sms", "send", number, text)
            time.sleep(6)  # give Linda time to score it and post its warning
            return f"SMS from {number} delivered."
        if cmd == "call":
            if not valid_number(args):
                raise ValueError("use: /phone call 0700000777")
            p.adb("emu", "gsm", "call", args)
            self.ringing.add(args)
            time.sleep(5)
            return f"{args} is calling. End it with /phone hangup."
        if cmd == "hangup":
            for number in list(self.ringing) or ([args] if valid_number(args) else []):
                p.adb("emu", "gsm", "cancel", number)
            self.ringing.clear()
            time.sleep(1)
            return "Call ended."
        if cmd == "tap":
            if not args:
                raise ValueError("use: /phone tap Settings")
            if p.tap_exact(args, scrolls=0) or p.tap_text(args, scrolls=2):
                return f"Tapped “{args}”."
            raise ValueError(f"nothing with the words “{args}” on screen (try /phone tapxy x y)")
        if cmd == "tapxy":
            m = re.fullmatch(r"(\d{1,4})\s+(\d{1,4})", args)
            if not m:
                raise ValueError("use: /phone tapxy 540 1200")
            p.shell(f"input tap {m.group(1)} {m.group(2)}")
            time.sleep(1.5)
            return f"Tapped at {m.group(1)}, {m.group(2)}."
        if cmd == "type":
            if not args or len(args) > 500:
                raise ValueError("use: /phone type some words (up to 500 characters)")
            p.type_text(args)
            return "Typed."
        if cmd in ("back", "home", "enter", "recents"):
            key = {"back": "BACK", "home": "HOME", "enter": "ENTER", "recents": "APP_SWITCH"}[cmd]
            p.shell(f"input keyevent KEYCODE_{key}")
            time.sleep(1.5)
            return f"Pressed {cmd}."
        if cmd == "scroll":
            if args not in ("up", "down"):
                raise ValueError("use: /phone scroll down, or /phone scroll up")
            p.swipe_down() if args == "up" else p.swipe_up()
            return f"Scrolled {args}."
        if cmd == "rotate":
            self.landscape = not self.landscape
            p.shell("settings put system accelerometer_rotation 0")
            p.shell(f"settings put system user_rotation {1 if self.landscape else 0}")
            time.sleep(3)
            return "Sideways." if self.landscape else "Upright."
        if cmd == "open":
            p.open_app()
            return "Opened Linda."
        if cmd in ("notifications", "notes"):
            return "Linda's notifications are listed below."
        if cmd == "install":
            apk = self.apk
            if args == "latest":
                apk = str(p.out / "latest.apk")
                self.gh.latest_apk(Path(apk))
            ok, out, role = p.install_linda(apk)
            if not ok:
                raise ValueError("install failed: " + out.strip()[-200:])
            p.open_app()
            which = "the newest latest-build" if args == "latest" else "this session's build"
            return f"Installed {which} (fresh data). Call-screening role: {'yes' if role else 'no'}."
        if cmd == "wait":
            seconds = min(int(args), 30) if args.isdigit() else 5
            time.sleep(seconds)
            return f"Waited {seconds} s."
        if cmd == "stop":
            self.stopped = True
            return "Stopping the session."
        raise ValueError(f"unknown command “{cmd}” (try /phone help)")

    def answer(self, comment: dict, release: dict, issue: int) -> None:
        commands = parse_commands(comment.get("body", ""))
        if not commands:
            return
        lines, show_help = [], False
        for cmd, args in commands:
            shown = f"`/phone {cmd}{' ' + args if args else ''}`"[:120]
            try:
                lines.append(f"- {shown}: {self.run(cmd, args)}")
            except Exception as e:  # noqa: BLE001 - report it and keep the session alive
                lines.append(f"- {shown}: **failed**, {e}")
            show_help = show_help or cmd == "help"
            if self.stopped:
                break
        shot = self.phone.screenshot(f"c{comment['id']}")
        try:
            # An <img> tag (not ![]()) so the screenshot shows at phone size instead of filling the page.
            image = f'<img src="{self.gh.upload(release, self.phone.out / shot)}" width="300" alt="screen">'
        except Exception as e:  # noqa: BLE001
            image = f"(screenshot upload failed: {e})"
        notes = self.phone.linda_notifications()
        text = self.phone.screen_text().replace("```", "'''")[:3000]
        body = [f"@{comment['user']['login']} done:", *lines, "",
                "**Linda's notifications:** " + ("; ".join(notes) if notes else "none"), "",
                image, "",
                "<details><summary>Text on screen</summary>\n\n```\n" + text + "\n```\n</details>"]
        if show_help:
            body += ["", HELP]
        self.gh.comment(issue, "\n".join(body))


def redact(text: str) -> str:
    """Never post the screen key, even inside an error message or a log line."""
    key = os.environ.get("LIVE_PHONE_KEY", "")
    return text.replace(key, "<key>") if key else text


def log_tails(out: Path) -> str:
    """The last lines of the helpers' logs, for a session that went wrong (the key is blanked out)."""
    parts = []
    for name in ("cloudflared.log", "ws-scrcpy.log", "gate.log", "screen-check.txt"):
        f = out / name
        if f.exists():
            lines = f.read_text(errors="replace").splitlines()[-12:]
            parts.append(f"--- {name}\n" + "\n".join(lines))
    return redact("\n".join(parts).replace("`", "'"))


def main() -> int:
    ap = argparse.ArgumentParser(description="Live phone command listener")
    ap.add_argument("--apk", required=True)
    ap.add_argument("--minutes", type=int, default=60)
    ap.add_argument("--screen-url", default="", help="tunnel address of the screen, without the key")
    ap.add_argument("--out", default="live-phone-out")
    args = ap.parse_args()
    gh = GitHub(os.environ["GITHUB_REPOSITORY"], os.environ["GITHUB_TOKEN"])
    issue = gh.issue()
    try:
        return run_session(args, gh, issue)
    except Exception:  # noqa: BLE001 - say what went wrong where the team is looking, then fail the run
        gh.comment(issue, "Live phone **failed** and has stopped:\n\n```\n"
                   + redact(traceback.format_exc()[-2500:]) + "\n" + log_tails(Path(args.out)) + "\n```")
        raise


def run_session(args, gh: GitHub, issue: int) -> int:
    phone = Phone(Path(args.out))
    session = Session(phone, gh, args.apk)
    release = gh.fresh_release(os.environ.get("GITHUB_SHA", "HEAD"))
    phone.prepare()
    ok, out, role = phone.install_linda(args.apk)
    phone.open_app()
    installed = f"Linda installed: {'yes' if ok else 'NO, ' + out.strip()[-150:]}; call-screening role: {'yes' if role else 'no'}."
    # Results of the optional screen self-check (screen_check.py ran before this script).
    check = ""
    if (phone.out / "screen-check.txt").exists():
        pics = []
        for pic in sorted(phone.out.glob("screen-check-*.png")):
            try:
                pics.append(f'<img src="{gh.upload(release, pic)}" width="420" alt="{pic.stem}">')
            except Exception as e:  # noqa: BLE001
                pics.append(f"({pic.name} upload failed: {e})")
        check = ("\n\n<details><summary>Screen self-check (a browser on the runner opened the link)</summary>\n\n```\n"
                 + log_tails(phone.out) + "\n```\n" + " ".join(pics) + "\n</details>")

    minutes = max(5, min(args.minutes, 300))
    started = datetime.now(timezone.utc)
    ends = started + timedelta(minutes=minutes)
    run_url = f"{os.environ.get('GITHUB_SERVER_URL', 'https://github.com')}/{gh.repo}/actions/runs/{os.environ.get('GITHUB_RUN_ID', '')}"
    if args.screen_url:
        screen = (f"**Screen:** open `{args.screen_url}/YOUR-KEY/` with your `LIVE_PHONE_KEY` in place of `YOUR-KEY` "
                  "(the address only works with the key). Next to the phone, keep **proxy over adb** selected and "
                  "click **WebCodecs**; the phone opens in a new tab. If it stays black, try **Broadway.js**.")
    else:
        screen = ("**Screen:** off. Add the `LIVE_PHONE_KEY` secret (Settings → Secrets and variables → Actions) and "
                  "start a new session to get the browser screen. Commands below still work.")
    gh.comment(issue, f"### Live phone is on until {ends.astimezone(NAIROBI):%H:%M} Nairobi time ({minutes} min)\n\n"
               f"Build of `{os.environ.get('GITHUB_REF_NAME', '?')}` @ `{os.environ.get('GITHUB_SHA', '?')[:7]}`, "
               f"with every permission granted. {installed} [Session log]({run_url})\n\n{screen}{check}\n\n{HELP}")
    print(f"listening on issue #{issue} until {ends.isoformat()}", flush=True)

    seen: set[int] = set()
    since = started.strftime("%Y-%m-%dT%H:%M:%SZ")
    warned = False
    while datetime.now(timezone.utc) < ends and not session.stopped:
        try:
            for c in gh.new_comments(issue, since):
                if c["id"] in seen or c["created_at"] < since:
                    continue
                seen.add(c["id"])
                if is_trusted(c) and parse_commands(c.get("body", "")):
                    print(f"comment {c['id']} from {c['user']['login']}", flush=True)
                    session.answer(c, release, issue)
            if not warned and ends - datetime.now(timezone.utc) < timedelta(minutes=5):
                warned = True
                gh.comment(issue, "Live phone: **5 minutes left** in this session.")
        except Exception as e:  # noqa: BLE001 - a network hiccup must not end the session
            print(f"poll error: {e}", flush=True)
        time.sleep(POLL_S)
    gh.comment(issue, "Live phone session **ended**. Start a new one from Actions → Live phone → Run workflow.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
