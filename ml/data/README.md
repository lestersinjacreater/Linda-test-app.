# Linda training data

Status: **no dataset yet** (Phase 0). This file will be kept up to date as data is added.

## Schema

`processed/*.csv` uses these columns:

| column | values |
|---|---|
| `id` | unique id |
| `text` | message text, already scrubbed |
| `label` | `scam` or `legit` |
| `campaign` | scam campaign name (e.g. `reversal`, `fake_mpesa`), or `legit` |
| `source` | where it came from, e.g. `team_collected`, `public_template`, `synthetic` |
| `is_hard_negative` | `true` for real-looking legit messages that resemble scams |
| `language` | `en`, `sw`, `sheng` or `mixed` |

## Rules

- **Scam messages** come from team members and families. Scrub all phone numbers, names and M-Pesa codes before they enter `processed/`.
- **Legit messages** are built from public formats (M-Pesa, bank, KRA, delivery templates) and ordinary chat-style text. Never collect anyone's private SMS.
- **Hard negatives are mandatory**: reversals, Fuliza notices, bank OTPs, KRA reminders, delivery updates.
- `raw/` is gitignored. Raw data never gets committed.
- Anything synthetic or LLM-generated must be marked in `source` and listed below.

## Synthetic / generated data declared here

None yet.

## Consent

Team members contributing scam messages have agreed to share them for this project. (Record who and when here, without personal details.)
