# dashboard/: live demo dashboard (Next.js, TypeScript, Tailwind)

Dark, vivid, creative layout; readable on a projector from the back of a room.

## Structure
```
dashboard/src/
  app/                 page.tsx (single demo screen), layout.tsx
  features/
    radar-map/         Kenya map, phones as dots (grey idle, amber received, red detected, green warned)
    blast-timeline/    scrolling event feed + time-to-protection clock
    metrics/           big numbers from GET /metrics
    controls/          Reset, Start blast, Rotating, Poison test, Call (presenter only, hotkeys)
    campaigns/         confirmed senders with category and report count
  lib/                 ws client with reconnect, api client, fallback data
```

## Rules
- Connect to `NEXT_PUBLIC_TELCO_WS` / `NEXT_PUBLIC_TELCO_API`; reconnect automatically.
- Fallback demo mode: if the WebSocket is down for 5 s, replay a recorded event log from
  `lib/fallback/demo-events.json` so the stage never shows a blank screen. Show a small "replay"
  badge so the team knows.
- Keep state in memory; no browser storage needed.
- Never display message text from real phones; the dashboard only shows simulator events.
