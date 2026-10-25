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

**Limits:** an emulator is not a budget phone. It does not kill background apps, and installing with adb skips Play
Protect and Android's restricted settings. Do one real-phone run before the demo.
