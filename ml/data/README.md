# Linda training data

Status: **real Kenyan data in use.** Training uses synthetic templates, two foreign public datasets (Tanzanian Swahili and
international English) and a public Kenyan SMS set (Salim Mwatsefu, IwazoLab, used with permission) that Claude re-labelled
by hand. Messages collected by the team itself are still welcome: they would be the first Kenyan data we gathered ourselves.

## Getting the real data (each person, on their own machine)

Raw and processed real data are git-ignored. To rebuild them:

1. Download the Kaggle "Swahili SMS Detection Dataset" (henrydioniz) and save it as `ml/data/raw/bongoscam.csv`.
2. Download the Mendeley "SMS Phishing Dataset" (Mishra and Soni), unzip it, and save `Dataset_5971.csv` as
   `ml/data/raw/mendeley_smishing.csv`.
3. Run `make data` (builds `processed/synthetic.csv` and `processed/public.csv`), then `make train`.

4. Kenyan SMS: download `data/raw/scam_detection_labeled.csv` from github.com/Salimmwatsefu/NLP-Scam-Detection and save it
   as `ml/data/raw/kenya_sms_mwatsefu.csv`. `make data` builds `processed/kenya_sms.csv` from it using OUR labels in
   `ml/data/labels/kenya_sms_labels.csv` (fingerprints and labels, no text), not the file's own keyword-script labels.

Kenyan team messages go in `processed/team_collected.csv` with the schema below (`source` = `team_collected`).
The trainer picks the file up automatically; put whole scam campaigns in `test` so the score shows how
Linda handles Kenyan scam scripts it never saw.

## Schema

`processed/*.csv` uses these columns:

| column | values |
|---|---|
| `id` | unique id |
| `text` | message text, already scrubbed |
| `sender` | sender ID or `2547XXXXXXXX` number (scrubbed to a placeholder). The model uses it as context |
| `split` | `train`, `val` or `test`. `test` is reserved for REAL data only |
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

`ml/data/processed/synthetic.csv`, about 2,300 rows made by `ml/src/features/dataset/synthetic.py` from hand-written templates (seed 42, run `make data`). `source` is `synthetic`. Whole templates are held out for `val`. There is no synthetic `test` split. Scam wordings were written by the team and Claude Code from scam types the team described; legit messages follow public M-Pesa, bank, KPLC and KRA formats. All phone numbers are fake placeholders.

## Consent

Team members contributing scam messages have agreed to share them for this project. (Record who and when here, without personal details.)
