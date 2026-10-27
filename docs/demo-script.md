# Linda demo script (hack day, 28–29 Oct)

About 7 minutes. Two people: the **presenter** talks, the **driver** works the phones and the laptop.
Every moment has a fallback, so the demo never stops when the network does.

## Before the show (30 minutes before)

1. Run `make prod-demo-reset` on the server (or `make demo-reset` on the backup laptop). It clears the gateway's reports
   and confirmations, resets the mock fraud team and the telco, and restarts the gateway so it pulls the fraud team's
   list again. The dashboard map is empty after it.
2. Open two tabs on the projector: the dashboard's main page (real numbers, pill **LIVE**) and `/simulation` (badge
   **LIVE**). If the simulation says REPLAY, the WebSocket is down and it plays its recorded run: carry on, everything
   below still works.
3. **Demo phone** (Android, latest APK): Settings, tap the version number 7 times, check the radar address, tap
   **Save and sync now**. The consent card says **Agreed**. Clear History. Battery saver off. Volume up.
4. **Scammer phone** (any phone with a SIM): the texts below are ready in its drafts.
5. **Basic phone** on the table, its number in `REAL_PHONES` (only if the organisers allowed a live Africa's Talking account).
6. Mobile data on for the demo phone. If the venue network is bad, use demo mode (Settings, version number 7 times,
   **Open demo mode**): every message below is there and needs no network.

## The moments

| # | What the driver does | What the judges see | What the presenter says |
|---|---|---|---|
| 0 | Open the dashboard's main page (the server's address) | Real numbers from real phones: phones with Linda, scams caught, numbers confirmed | "Safaricom already has powerful anti-fraud systems, but they work behind the scenes. Linda takes them down to the customer's phone, where a machine-learning model checks every SMS and call and warns before the scam is opened or answered. The model keeps getting smarter: we retrain it on new scams, and each phone learns privately from its owner. Every phone that catches a scam feeds Safaricom's fraud team back. These numbers are real phones doing that today." |
| 1 | Open `/simulation` on the dashboard | A quiet map of Kenya (a simulation of the network) | "This is Safaricom's network on an ordinary afternoon." |
| 2 | Scammer phone texts the demo phone the **fake M-Pesa** text | A warning on the lock screen before it is opened: "This looks like an M-Pesa message but did not come from M-Pesa" | "Safaricom's protection, on her phone: Linda's model read it on the device, with no server, and warned before she opened it." |
| 3 | Scammer phone **calls** the demo phone; then demo mode, **Simulate a call from Safaricom Customer Care** | "Likely scam caller" while ringing; then "Verified: Safaricom Customer Care" | "Impersonation fails both ways: the scammer is labelled, the real Safaricom is verified." |
| 4 | Scammer phone texts **"Nimekutumia 2,500 kimakosa, tafadhali nirudishie haraka"** (the demo phone must not have a real Ksh 2,500 from M-Pesa in the last 2 days) | "Your real M-Pesa messages show no Ksh 2,500 received... Nobody sent you this money" | "Linda checked her real M-Pesa messages, on the phone. This is a fact, not a guess." |
| 5 | Scammer phone texts **"Hi mum, this is my new number. Please send me Ksh 3,000 for fare"** | A caution: "Someone says this is their new number and asks for money. Call their old number..." | "Linda knows who she is really in touch with. A stranger claiming to be family is the trick." |
| 6 | Scammer phone texts **"Hello tenant, kindly note our new Paybill for rent is 400200, account 12B."** | A caution: "This says the way to pay has changed..." | "Rent and school-fee scams change the payment details. Linda says: call the number you already know." |
| 7 | Dashboard: **Start blast** | Red dots as Linda phones detect; reports flow (no text); the gateway confirms after 3 phones; a green wave of warnings to phones **without** Linda, basic phones included | "Safaricom's protection, on every phone: each Linda user is a sensor. Three caught it; Safaricom confirmed it and warned everyone who received it, on any phone, in minutes." |
| 8 | The basic phone on the table | It receives the warning SMS | "That phone has no app. It is protected anyway." |
| 9 | `make demo-adoption` on the laptop | Protection rises with adoption | "At 1% adoption, a blast to 500 people reaches 3 Linda phones about 88% of the time." |
| 10 | A judge taps **Report** on a warning on their own Linda phone; the projector shows `/fraud-team` (the fraud-team console). Press **Confirm scam** on their number | Within seconds the number appears in the review queue ("NEW"); after Confirm it says "Sent to all phones" and the activity feed turns red | "One tap, and Safaricom's fraud team has it: the number and the scam type, never the text. One click, and every Linda phone is protected from it." |
| 11 | Point at the metrics panel | Time to first detection, time to confirmation, % warned before reading | "Seconds to detect, under a minute to confirm, nearly everyone warned before reading." |

**Optional, USSD on the basic phone:** dial the Africa's Talking sandbox code in their web simulator (or a real code, if
the organisers allow one), choose 1 and enter the scammer's number: "WARNING: ... is a confirmed scam number". No app, no
data bundle needed.

**Optional, payment precheck:** `curl -d '{"recipient":"254700000777","amount":1500}' -H 'content-type: application/json'
https://DOMAIN/radar/v1/payments/precheck` answers `allow: false` with the reason.

**Optional, if there is time:** on the demo phone, Home, **Scan my inbox**. On a phone with real history it shows what
Linda would have caught, and that it now stays quiet on SAF_Balance, Airtel and bank texts (the inbox story).

## If something breaks

| Problem | Do this |
|---|---|
| No SMS signal at the venue | Demo mode: every text above is a button, through the same pipeline as a real SMS. |
| The server is unreachable | The dashboard plays its recorded run (REPLAY badge). Moments 1 to 6 need no server at all. |
| The blast never confirms | `make prod-demo-reset`, then Start blast again. Each run uses fresh scammer numbers. |
| The basic phone gets nothing | Say so honestly: the sandbox reaches only Africa's Talking's web simulator; show that instead. |
| A warning does not appear on the demo phone | Check the notification is not silenced; then use demo mode. |

## Honest lines, if a judge asks

- **Numbers:** model results are on data it never trained on, per source (`ml/reports/metrics.md`). The donated inbox
  replay: genuine texts wrongly warned 7.6% → 1.1%, with the same scams caught (EXPLAINED.md, "The inbox story").
- **What is simulated:** the telco network and the fraud team's systems (`simulator/`, `fraudintel/`). The app, the model
  and the gateway are real. In production the gateway plugs into Safaricom's systems, and Linda lives in My OneApp.
- **The demo trust shortcut:** simulated phones start with high trust so 3 can confirm (`RADAR_DEMO_TRUSTED_PREFIX`).
  It is switched off after the hackathon.
