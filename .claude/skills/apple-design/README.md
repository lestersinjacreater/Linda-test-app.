# apple-design (third-party skill, MIT)

Copied unchanged from https://github.com/emilkowalski/skills (`skills/apple-design/SKILL.md`), Copyright (c) 2026 Emil Kowalski,
MIT licence (see `LICENSE` in this folder). It is guidance for Claude Code about fluid motion and typography, written for the web.

How we use it in Linda:
- The Android app is Jetpack Compose, so the principles apply but its CSS and JavaScript do not: springs become Compose `spring(...)`,
  `prefers-reduced-motion` becomes the phone's "remove animations" setting, `backdrop-filter` becomes a translucent surface.
- The `dashboard/` and `site/` are web, so more of it applies directly there.
- **`docs/design-system.md` wins on any conflict** (for example it keeps Poppins and Inter instead of the system font, a "Home" tab,
  and allows only one show-off animation, the scan sweep).
- We copy no code from it. It is guidance, not a library.
