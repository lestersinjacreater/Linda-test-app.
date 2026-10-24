# Virtual phone

An Android 14 emulator on GitHub's servers that installs Linda and tests it like a person would, so nobody has to
download the app to check a change.

**What it checks** (`tour.py`): install with permissions and the call-screening role; welcome screen; Home says
"LINDA is protecting you"; a **real SMS** (the emulator receives it) of the fake M-Pesa text from 0700 000 777 is
warned with its reason and buttons, without showing the message; tapping it opens the stop screen, the detail and the
report sheet; an ordinary chat is not warned; a **real call** from the same number is labelled "Likely scam caller"
while ringing; History, Check and Settings (build number); the verified badge for 0722 000 000; Kiswahili.

**Run it:** GitHub, Actions tab, "Virtual phone", "Run workflow". It also runs on every pull request that changes the
app. About 15 minutes.

**Results:** the run's summary shows the report; the screenshots are in the run's artifacts and in the
`virtual-phone-latest` pre-release (Releases page), named after each step.

**Run it against your own phone or emulator** (adb connected):

```
python3 tools/virtual-phone/tour.py \
  --apk linda-debug.apk --out out
```

## Live phone: a phone you use yourself, in your browser

The same emulator, but it stays on (5 to 300 minutes) so you can scroll, tap and type on it, and anyone with repo
access (including Claude) can send it real texts and calls.

**Once:** add a repo secret `LIVE_PHONE_KEY` (Settings → Secrets and variables → Actions → New repository secret).
Any phrase of letters, digits and dashes. It is the password for the screen; never post it.

**Start a session:** Actions → **Live phone** → **Run workflow**, choose the minutes. In about 5 minutes the
**Live phone** issue gets a comment with the screen address: open `https://<address>/<your key>/`, keep
**proxy over adb**, click **WebCodecs** (or **Broadway.js** if it stays black). The phone opens in a new tab.

**Commands:** post a comment on the Live phone issue with one or more lines like these. The phone answers in about
10 seconds with what happened, a screenshot, Linda's notifications and the text on screen.

```
/phone sms 0700000777 Nimekutumia 2,500 kimakosa
/phone call 0700000777
/phone hangup
/phone tap See why
/phone shot
/phone install latest
/phone stop
```

`/phone help` lists them all. Only the repo owner and collaborators are obeyed; everyone else is ignored.

**How it fits together** (all on the GitHub runner): ws-scrcpy shows the screen on `127.0.0.1:8000` under
`/<key>/`; `gate.py` in front of it refuses any connection whose path does not start with the key; cloudflared gives
the gate a temporary public https address; `listen.py` reads the issue every 5 seconds and drives the phone with the
same helpers as the tour (`phone.py`).

**Be careful:** the issue is public. Use fake numbers (`0700000xxx`) and test messages only, never a real person's
SMS. A session uses a GitHub runner for as long as it is on (free for public repos); `/phone stop` ends it early.

**Limits:** an emulator is not a budget phone. It does not kill background apps, and installing with adb skips Play
Protect and Android's restricted settings. Do one real-phone run before the demo.
