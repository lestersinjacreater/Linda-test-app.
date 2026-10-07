# design-system.md — LINDA

> **Five layers. One shield.**
> Language · Intelligence · Network · Decision · Alert

You are building **LINDA**, a Safaricom scam-protection app for Android (Jetpack Compose).
LINDA must feel like it belongs in the Safaricom family next to M-PESA: confident Safaricom green, calm,
human, and instantly readable by a grandmother on a cheap 720p phone in Nairobi sunlight.

This file is the visual source of truth. Read it before building any screen.
If it ever conflicts with CLAUDE.md, CLAUDE.md wins and this file is updated.

> **Implementation notes (added by the team while building, kept apart from the original text):**
> - Text on a tinted background uses the risk colour's *text* shade (see 3.3 note), because `#00A651` on `#E8F6EE` is only ~3:1.
> - The Layer Trace shows only what the app really computed (see `docs/EXPLAINED.md`); it never invents a figure.
> - Folder names under `features/` are NOT renamed to the five layers; the layers are mapped in one place, `LayerTrace`.
> - Dark mode follows the phone's system setting.

---

## 1. The big idea: "The name is the shield"

LINDA's design is built on one concept: **every message passes through five layers, and the user can see them.**

The five letters are not just a logo. They are the app's core visual element:

```
 ┌───┬───┬───┬───┬───┐
 │ L │ I │ N │ D │ A │   ← The Layer Trace
 └───┴───┴───┴───┴───┘
  Language → Intelligence → Network → Decision → Alert
```

They appear as:
- the **logo** (five segments forming a shield),
- the **home status ring** (five arcs, one per layer),
- the **Layer Trace** on every verdict (which layers fired, and why),
- the **scan animation** (layers light up left to right),
- the **code structure** (`features/language`, `features/intelligence`, `features/network`, `features/decision`, `features/alert`).

When a judge watches a scam arrive, they should *see* the five layers work. That is the design's job.

---

## 2. Design principles

1. **Calm protector, not alarm bell.** LINDA is confident and quiet. It speaks up only when it matters, then speaks clearly.
2. **Show the reasoning.** Never a bare score. Every verdict shows which layers fired and why.
3. **One next step.** Every warning ends with one obvious action.
4. **Safaricom family.** Familiar green, familiar warmth, nothing that feels like a foreign app.
5. **Built for everyone.** Big text, plain words, English and Swahili, icons plus labels, never colour alone.

---

## 3. Colour

### 3.1 Safaricom brand greens (identity)

| Token | Hex | Use |
|---|---|---|
| `green500` Safaricom Green | `#00A651` | Brand moments: logo, hero areas, status ring, large fills |
| `green700` Deep Green | `#007A3D` | Primary buttons, links, active tabs (passes contrast with white text) |
| `green900` Forest | `#00602F` | Headers, pressed states, dark accents |
| `green300` Fresh Green | `#2DBE6C` | Highlights, progress, dark-mode primary |
| `green50` Mint | `#E8F6EE` | Tinted cards, selected rows, Safe verdict background |

**Contrast rule:** white text on `green500` is only allowed at 18sp bold or larger. For buttons and small text, use `green700`.

### 3.2 Neutrals

| Token | Light mode | Dark mode | Use |
|---|---|---|---|
| `background` | `#F6F8F7` | `#08130D` | Main canvas |
| `surface` | `#FFFFFF` | `#11211A` | Cards, sheets |
| `surfaceRaised` | `#EEF2F0` | `#1A2E24` | Inputs, pressed cards, dialogs |
| `textPrimary` | `#0F1A14` | `#F2F7F4` | Headings, body |
| `textSecondary` | `#5B6B62` | `#9DB0A5` | Captions, helper text |
| `border` | `#DCE4DF` | `#2A4035` | Dividers, outlines |

**Light mode is the default** (readable in sunlight, matches Safaricom apps). Dark mode is fully supported.

### 3.3 Risk colours (reserved — only for verdicts)

| Level | Light | Dark | Icon | Label EN / SW |
|---|---|---|---|---|
| **Safe** | `#00A651` on `#E8F6EE` | `#2DBE6C` on `#0F2A1C` | Shield + check | "Looks safe" / "Inaonekana salama" |
| **Caution** | `#8A5A00` on `#FFF4D6` | `#FFC940` on `#2E2610` | Triangle + ! | "Be careful" / "Kuwa mwangalifu" |
| **Scam** | `#E4002B` on `#FDE8EC` | `#FF5C77` on `#33121A` | Octagon + ! | "Likely scam" / "Huenda ni ulaghai" |

Rules:
- Safe naturally shares Safaricom green: **green means protected.** That's intentional.
- Scam uses the M-PESA-style red. It appears ONLY on Scam verdicts and the Scam layer state.
- Never show risk by colour alone: icon + label, always.

### 3.4 Layer tints (for the Layer Trace only)

Each layer has a shade on one green ramp, so the trace reads as a single shield getting stronger left to right:

| Layer | Tint |
|---|---|
| L · Language | `#B7E4C7` |
| I · Intelligence | `#74C69D` |
| N · Network | `#2DBE6C` |
| D · Decision | `#00A651` |
| A · Alert | `#007A3D` |

A layer that **flags a risk** switches to the verdict colour (Caution amber or Scam red) instead of its tint.

---

## 4. Typography

- **Headings and the LINDA wordmark:** Poppins, SemiBold/Bold (rounded, friendly, Safaricom-adjacent feel)
- **Body and UI:** Inter, Regular/Medium
- **Numbers (amounts, counts, scores):** Inter with tabular figures
- **Layer letters (L·I·N·D·A):** Poppins Bold, uppercase, +8% letter spacing
- Bundle fonts in `res/font`. No runtime downloads.

| Role | sp | Weight |
|---|---|---|
| Display (home status) | 32 | Bold |
| Title | 24 | SemiBold |
| Heading | 20 | SemiBold |
| Body Large (warnings, reasons) | 18 | Regular |
| Body (default) | 16 | Regular |
| Label | 14 | Medium |
| Caption | 12 | Regular (never for important info) |

Swahili runs ~30% longer than English. Never fix text widths. Test at 130% font scale.

---

## 5. Spacing, shape and depth

**Spacing:** 4dp base. Only use 4, 8, 12, 16, 24, 32, 48, 64dp. Screen padding 16dp, card padding 16dp (24dp for verdict cards), section gap 32dp.

**Radius:**

| Size | Value | Use |
|---|---|---|
| Small | 8dp | Inputs, chips, layer segments |
| Medium | 16dp | Cards, buttons |
| Large | 24dp | Verdict cards, bottom sheets |
| Full | Circle | Avatars, pills, status ring |

**Depth:** soft, green-tinted shadows in light mode (`0 4dp 16dp rgba(0,96,47,0.10)`); lighter surfaces instead of shadows in dark mode. Scam verdict cards get a red glow (`0 0 24dp rgba(228,0,43,0.25)`), used nowhere else.

**Signature shape:** the **shield notch**. Hero cards and the home header have one rounded corner cut at the bottom-right (24dp), echoing a shield. Use it only on hero elements so it stays special.

---

## 6. The five layers: visual language

Each layer has a letter, a name, an icon, and a one-line explanation shown to users.

| Layer | Icon (Material Symbols Rounded) | User-facing line (EN) | (SW) |
|---|---|---|---|
| **L · Language** | `translate` | "Read the message in English, Swahili or Sheng" | "Imesoma ujumbe kwa Kiingereza, Kiswahili au Sheng" |
| **I · Intelligence** | `neurology` | "Checked it against thousands of known scams" | "Imeulinganisha na maelfu ya ulaghai unaojulikana" |
| **N · Network** | `hub` | "Checked who sent it and how" | "Imechunguza aliyeutuma na jinsi ulivyotumwa" |
| **D · Decision** | `balance` | "Weighed everything together" | "Imepima kila kitu pamoja" |
| **A · Alert** | `shield` | "Protected you" | "Imekulinda" |

Each segment has four states:

| State | Look |
|---|---|
| Idle | Outline only, `border` colour, letter in `textSecondary` |
| Scanning | Filled with layer tint, gentle shimmer |
| Passed | Filled with layer tint, small check |
| Flagged | Filled with Caution or Scam colour, small "!", letter in white |

---

## 7. Signature components

### 7.1 Layer Trace (the hero component)
A horizontal row of five rounded segments (Small radius, 8dp gaps), each showing its letter.
- Appears on every verdict card, in the notification detail screen, and in demo mode.
- Tapping a segment expands it to show that layer's findings, for example:
  - **L:** "Undid a disguise: 'M-P3SA' → 'M-PESA'"
  - **I:** "94% similar to 'sent by mistake' scams"
  - **N:** "Unknown 07XX number · not in your contacts · contains a link"
  - **D:** "Verdict: Likely scam"
  - **A:** "Warning shown · guardian alerted"

### 7.2 Shield Status Ring (home screen)
A large circle (200dp) made of **five arcs**, one per layer, in the layer tints.
- All five filled: "LINDA is protecting you" / "LINDA inakulinda".
- A missing permission dims the relevant arc and shows a fix button (e.g. N dims if contacts access is off).
- Centre: shield wordmark. Below: "**12** scams stopped this month" in tabular figures.
- Idle animation: one slow "breath" every 6 seconds (scale 1.00 → 1.02). Respect reduce-motion.

### 7.3 Verdict Card
Large radius, 24dp padding, risk background tint, 4dp left border in risk colour.
1. Risk icon (32dp) + label (Heading), in the risk colour.
2. Message preview (Body, max 3 lines), sender and time (Caption).
3. **Layer Trace.**
4. "Why LINDA flagged this": reasons as separate lines, Body Large.
5. **One primary action:** "Don't send money" / "Usitume pesa".
6. Secondary: "I already sent money →" (opens Recovery) and "Mark as safe".

### 7.4 Scam Alert Takeover
For high-confidence scams when the user opens the warning:
- Full-screen sheet, Scam background tint, octagon icon at 64dp.
- Headline at 24sp: "Stop. This looks like a scam." / "Simama. Huu unaonekana kuwa ulaghai."
- Voice warning plays (TextToSpeech), reading exactly the on-screen headline and top reason.
- Two short haptic pulses on appearance.

### 7.5 Guardian Alert Card
Shown on the guardian's phone: avatar of the protected person, Caution style, "Mum received a suspected scam at 4:12 PM. Consider calling her." Primary button: "Call Mum".

### 7.6 Recovery Timeline
A vertical stepper in Safaricom greens: Act fast → Request reversal → Report to 333 → Contact your bank → Report to police. The current step pulses gently; done steps get a check. A countdown chip at the top: "Reversals work best within minutes."

### 7.7 Fake M-PESA Badge
When a message imitates an M-PESA confirmation from a non-M-PESA sender, show a crossed-out M-PESA-style receipt icon with: "Not from M-PESA. You have not received any money."

### 7.8 Demo Mode Overlay (hack day)
Hidden toggle (tap the logo 7 times). Shows a translucent panel at the bottom of the screen with the Layer Trace in real time and the raw outputs of each layer (normalised text, model score, network signals, final decision, actions taken), in a monospaced font. This is what judges watch during the live demo.

### 7.9 Standard components
- **Buttons:** 52dp tall, Medium radius, 24dp horizontal padding. Primary = `green700` fill, white text. Secondary = outline in `border`, `textPrimary`. Pressed: scale 0.97, darken 8%. No hover states. 48dp minimum touch target.
- **Inputs:** 52dp, Small radius, `surfaceRaised` fill, 1dp `border`; focus 2dp `green500`.
- **Cards:** `surface`, Medium radius, 16dp padding, soft green shadow.
- **Chips:** Full radius, 32dp tall, Label text.

---

## 8. Motion: "the scan sweep"

The signature animation, used whenever LINDA analyses a message (SMS arrival, paste check, inbox scan):
1. Segments light up **left to right**, L → A, 70ms apart, 200ms each, ease-out.
2. A flagged layer lands with a tiny shake (2dp, 120ms) and switches to its risk colour.
3. The verdict card then rises 16dp and fades in (250ms).

Total under 600ms: fast enough to feel instant, slow enough for judges to see the five layers work.

Other motion: 200ms ease-out standard; no continuous flashing; respect the system "remove animations" setting.

**Haptics:** Caution = one short tick. Scam = two short pulses. Safe = none.

---

## 9. Voice and microcopy

LINDA speaks like a calm, trusted older sibling: short sentences, plain words, never blaming the user.

| Situation | English | Swahili |
|---|---|---|
| Protected | "LINDA is protecting you." | "LINDA inakulinda." |
| Scam | "Stop. This looks like a scam." | "Simama. Huu unaonekana kuwa ulaghai." |
| Fake M-PESA | "This didn't come from M-PESA. You haven't received any money." | "Ujumbe huu haukutoka M-PESA. Hujapokea pesa yoyote." |
| Caution | "Be careful with this one." | "Kuwa mwangalifu na huu." |
| Already sent | "Don't panic. Let's act fast." | "Usiogope. Tuchukue hatua haraka." |
| Empty history | "No scams yet. LINDA is watching." | "Hakuna ulaghai bado. LINDA inalinda." |

Never: blame ("You should have known"), jargon ("classifier confidence"), or ALL CAPS sentences.

---

## 10. Iconography and illustration

- **Icons:** Material Symbols Rounded, 24dp, weight 400, one style everywhere.
- **Illustrations:** flat, rounded shapes in the green ramp, with one warm accent (amber). Show real Kenyan everyday moments: a mama mboga with her phone, a boda rider at a stage, a grandmother on a basic phone. No stock-photo realism, no hooded-hacker clichés.
- **Logo:** five rounded segments arranged as a shield, coloured with the layer tint ramp, with the wordmark **LINDA** in Poppins Bold beside it.
- **Brand note:** use Safaricom colours freely. Confirm with organisers before using the official Safaricom or M-PESA logos in the app or deck.

---

## 11. Accessibility (non-negotiable)

1. Text contrast at least 4.5:1 (3:1 only for 18sp bold and larger).
2. Risk = icon + label + colour, always.
3. All tap targets at least 48dp.
4. Works at 130% font scale with no clipping.
5. Every icon button has EN and SW content descriptions.
6. Voice warnings read the same text shown on screen.
7. Test on the cheapest phone the team has, in daylight.

---

## 12. Code mapping

Design tokens live in `core/ui/theme/` (`Color.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`, `Motion.kt`).
Feature folders mirror the five layers so the code reads like the name:

```
features/
├── language/       L · normaliser, language detection
├── intelligence/   I · on-device ML model
├── network/        N · sender, contacts, links, call timing
├── decision/       D · fusion scorer, verdict
├── alert/          A · notifications, voice, guardian, recovery
└── ui/             shared screens: home ring, verdict card, layer trace, demo overlay
```

Name colour tokens exactly as in this file (`green500`, `scamRed`, `layerL`…), never raw hex in components.

---

## 13. Rules

1. Never introduce colours outside this palette.
2. Risk colours mean risk and nothing else. Green means protected.
3. Every verdict shows the Layer Trace. No bare scores.
4. One primary action per screen.
5. Only use the spacing scale and the radius assigned to each component.
6. Every user-facing string lives in `strings.xml` with a Swahili translation in `values-sw/strings.xml`.
7. The scan sweep is the only "show-off" animation. Everything else stays quiet.
8. When in doubt: more whitespace, bigger text, fewer words.
9. New component? Describe it in this file first, then build it.
