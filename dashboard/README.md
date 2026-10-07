# dashboard: live demo screen

Dark, big-type single screen for the demo (root `CLAUDE.md` section 10): a Kenya map of phones (grey quiet, amber received,
red caught on the device, green warned), the headline numbers, a plain-English event feed, the scam numbers the radar
confirmed, and presenter controls (hotkeys **R** reset, **B** blast, **O** rotating numbers, **P** poison test).

    npm install && npm run dev        # http://localhost:3000, expects the simulator on http://localhost:8000

If the simulator's WebSocket is down for 5 seconds, the screen replays a recorded real run (badge: REPLAY), so it is never blank.
Re-record it with `make record-fallback` after changing the model or the simulator.
It only shows simulator events: no message text from any phone ever reaches it.
