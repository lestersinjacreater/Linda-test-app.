"""Builds a SYNTHETIC training set from message templates (no real data exists yet).

Every row is declared synthetic in the `source` column and in DATASETS.md. This data is only
good enough to prove the pipeline and give a first baseline; it must be replaced or topped up
with real, scrubbed messages (ml/data/README.md) before any metric is believed.

Split rule: whole TEMPLATES are assigned to `train` or `val`, so validation messages come from
wordings the model never saw. There is deliberately NO synthetic `test` split: `test` is
reserved for real data (ml/CLAUDE.md).
"""
import csv
import random
from pathlib import Path

COLUMNS = ["id", "text", "sender", "label", "campaign", "source", "is_hard_negative", "language", "split"]

NAMES = ["JOHN KAMAU", "MARY ACHIENG", "PETER OTIENO", "JANE WANJIKU", "ALICE MUTHONI", "DAVID KIPROP",
         "GRACE NYAMBURA", "SAMUEL OMONDI", "FAITH WAMBUI", "BRIAN MWANGI", "ANN MUTUA", "KEVIN ONYANGO"]
BAD_URLS = ["http://safaricom-bonus.xyz", "https://mpesa-secure-login.com", "http://kra-refund.top",
            "https://bit.ly/mp-up24", "http://safaricom-delivery.click", "https://tinyurl.com/3x8k2",
            "http://mpesa-promo.xyz/claim", "https://fuliza-limit.top", "https://bit.ly/kra-rf"]
COMPANIES = ["Safaricom", "KCB", "Equity Bank", "a leading bank", "Naivas"]
AMOUNTS = [100, 150, 200, 300, 500, 750, 1000, 1200, 1500, 2000, 2500, 3000, 4500, 5000, 8000, 10000,
           14250, 15000, 20000, 30000, 50000, 70000, 100000]
FEES = [100, 200, 250, 300, 350, 400, 500, 800, 1000, 1500]


def _money(n: int) -> str:
    return f"{n:,}"


def _code(rng: random.Random) -> str:
    # M-Pesa codes: capitals and digits, at least one of each, start with a letter.
    while True:
        c = rng.choice("ABCDEFGHJKLMNPQRSTUVWXYZ") + "".join(rng.choice("ABCDEFGHJKLMNPQRSTUVWXYZ0123456789") for _ in range(9))
        if any(ch.isdigit() for ch in c):
            return c


def _phone(rng: random.Random) -> str:
    return f"0700000{rng.randint(0, 999):03d}"  # fake placeholder range


def _slots(rng: random.Random) -> dict:
    amt = rng.choice(AMOUNTS)
    return {
        "amt": _money(amt), "bal": _money(rng.choice(AMOUNTS) + rng.randint(0, 999)),
        "fee": _money(rng.choice(FEES)), "code": _code(rng), "code2": _code(rng),
        "name": rng.choice(NAMES), "phone": _phone(rng), "badurl": rng.choice(BAD_URLS),
        "date": f"{rng.randint(1, 28)}/{rng.randint(1, 12)}/26", "time": f"{rng.randint(1, 12)}:{rng.randint(0, 59):02d} {rng.choice(['AM', 'PM'])}",
        "n": rng.randint(5, 50), "co": rng.choice(COMPANIES), "parcel": rng.randint(1000000, 9999999),
        "otp": rng.randint(100000, 999999), "acct": rng.randint(1000, 9999), "units": f"{rng.uniform(5, 60):.1f}",
        "cost": rng.choice([0, 7, 13, 23, 29]), "tok": "-".join(str(rng.randint(1000, 9999)) for _ in range(5)),
    }


# (label, campaign, language, sender_kind, is_hard_negative, template)
# sender_kind: personal | spoof (looks official but is not verified) | promo | or a verified sender ID
S = []
def scam(campaign, lang, sender, text):
    S.append(("scam", campaign, lang, sender, False, text))
def legit(campaign, lang, sender, text, hard=True):
    S.append(("legit", campaign, lang, sender, hard, text))

# ---- scams ----
for t in ["Nimekutumia {amt} kimakosa, tafadhali nirudishie haraka",
          "Samahani nimekutumia pesa {amt} kwa makosa. Naomba unitumie tena kwa nambari hii {phone}",
          "Habari, nimetuma Ksh {amt} kwa nambari isiyo sahihi, tafadhali rudisha pesa hiyo",
          "Ndugu pesa zangu {amt} zimeingia kwa simu yako kimakosa, tafadhali zirudishe sasa hivi"]:
    scam("reversal", "sw", "personal", t)
for t in ["Hi, I sent you Ksh {amt} by mistake. Please send it back to this number urgently",
          "Sorry, wrong number. I transferred {amt} to you by mistake, kindly reverse it to {phone}",
          "Please return the Ksh {amt} I sent to you in error, it is an emergency"]:
    scam("reversal", "en", "personal", t)
for t in ["Bro nimetuma {amt} wrong number, please rudisha asap",
          "Msee nimekutumia {amt} kimakosa, nitumie back sasa hivi ni emergency",
          "Kuja uskie, nimetuma {amt} kwa number yako by mistake, rudisha fasta"]:
    scam("reversal", "sheng", "personal", t)
for sender in ("personal", "spoof"):
    for t in ["{code} Confirmed. You have received Ksh{amt}.00 from {name} on {date} at {time}. New M-PESA balance is Ksh{bal}.00.",
              "{code} Confirmed. Ksh{amt}.00 received from {name} {phone} on {date} at {time}. New M-PESA balance is Ksh{bal}.00. Kindly return the amount sent in error.",
              "{code} Confirmed. Ksh {amt}.00 credited to your M-PESA from {name}. Please send back Ksh {amt} to {phone}, sent in error."]:
        scam("fake_mpesa", "en", sender, t)
    scam("fake_mpesa", "sw", sender, "{code} Imethibitishwa. Umepokea Ksh{amt}.00 kutoka kwa {name} tarehe {date} saa {time}. Salio jipya la M-PESA ni Ksh{bal}.00. Tafadhali rudisha pesa ulizotumiwa kimakosa.")
for t in ["Hongera! Umeshinda Ksh {amt}. Tuma registration fee ya Ksh {fee} upate zawadi yako.",
          "Umechaguliwa mshindi wa promo ya Ksh {amt}. Lipa ada ya Ksh {fee} kupitia M-Pesa kupokea zawadi."]:
    scam("prize", "sw", "personal", t)
for t in ["CONGRATULATIONS! You have won Ksh {amt} in the Safaricom Mega Draw. Send Ksh {fee} processing fee to claim your prize.",
          "Your number won Ksh {amt} in our lucky draw! Pay Ksh {fee} tax to {phone} to receive your winnings."]:
    scam("prize", "en", "promo", t)
scam("prize", "sheng", "personal", "Msee umeshinda ya {amt} kwa promo, tuma {fee} ya processing ndio upate pesa yako")
for t in ["Dear customer, your Fuliza limit has been increased to Ksh {amt}. Click {badurl} to activate",
          "Fuliza limit upgrade: Pay Ksh {fee} activation fee to {phone} to unlock Ksh {amt} limit.",
          "Boost your M-Pesa limit to Ksh {amt}. Send Ksh {fee} to {phone} today."]:
    scam("fuliza_upgrade", "en", "promo", t)
scam("fuliza_upgrade", "sw", "personal", "Kiwango chako cha Fuliza kimeongezwa hadi Ksh {amt}. Bonyeza {badurl} kuthibitisha sasa")
for t in ["KRA: You are eligible for a tax refund of Ksh {amt}. Claim now at {badurl} and enter your ID and M-Pesa PIN",
          "KRA notice: your account is flagged. Pay penalty Ksh {fee} to {phone} to avoid prosecution."]:
    scam("kra_refund", "en", "personal", t)
scam("kra_refund", "sw", "personal", "Mpendwa mlipa ushuru, una marejesho ya KRA ya Ksh {amt}. Tuma Ksh {fee} ada ya usindikaji kupokea pesa.")
for t in ["Job offer: Safaricom is hiring {n} data clerks. Salary Ksh {amt}. Pay registration fee Ksh {fee} to {phone} to secure your slot.",
          "URGENT vacancy at {co}. Send Ksh {fee} interview fee to {phone}."]:
    scam("job_fee", "en", "personal", t)
for t in ["Kazi inapatikana Nairobi, mshahara Ksh {amt}. Lipa ada ya usajili Ksh {fee} kupitia M-Pesa ili upewe kazi.",
          "Tunatafuta wafanyakazi {n} wa ofisi. Tuma Ksh {fee} ada ya mahojiano kwa {phone} ili upate nafasi yako.",
          "Nafasi za kazi zimetoka! Mshahara Ksh {amt} kwa mwezi. Lipa ada ya kujisajili Ksh {fee} kwa {phone}."]:
    scam("job_fee", "sw", "personal", t)
for t in ["Get an instant loan of Ksh {amt} with no CRB check. Pay Ksh {fee} insurance fee first to {phone}.",
          "Loan approved Ksh {amt}! Send Ksh {fee} processing fee to receive funds in your M-Pesa."]:
    scam("loan_fee", "en", "personal", t)
scam("loan_fee", "sw", "personal", "Pata mkopo wa Ksh {amt} bila CRB. Lipa Ksh {fee} ya bima kwanza kwa {phone}.")
for t in ["Safaricom: Your M-Pesa account will be suspended. Reply with your M-Pesa PIN to verify your account.",
          "Dear customer, we detected unusual activity. Send your PIN to {phone} to unblock your M-Pesa."]:
    scam("pin_request", "en", "spoof", t)
for t in ["Tafadhali tuma PIN yako ya M-Pesa ili tuthibitishe akaunti yako, la sivyo itafungwa leo.",
          "Kuna shida na akaunti yako. Tuma nambari yako ya siri (PIN) kwa {phone} ili irekebishwe."]:
    scam("pin_request", "sw", "personal", t)
for t in ["Your parcel is held at customs. Pay Ksh {fee} delivery fee at {badurl} to release it.",
          "Dear customer verify your M-Pesa account at {badurl} within 24 hours or it will be locked",
          "Safaricom: you have won free data. Claim at {badurl}"]:
    scam("phishing_link", "en", "promo", t)
scam("phishing_link", "sw", "personal", "Akaunti yako ya M-Pesa imefungwa. Ifungue hapa {badurl} sasa hivi")

# ---- legitimate messages (all hard negatives unless plain chat) ----
for sender in ("MPESA", "M-PESA"):
    legit("mpesa_received", "en", sender, "{code} Confirmed. You have received Ksh{amt}.00 from {name} {phone} on {date} at {time}. New M-PESA balance is Ksh{bal}.00. Transaction cost, Ksh0.00.")
    legit("mpesa_received", "sw", sender, "{code} Imethibitishwa. Umepokea Ksh{amt}.00 kutoka kwa {name} {phone} tarehe {date} saa {time}. Salio jipya la M-PESA ni Ksh{bal}.00.")
    legit("mpesa_sent", "en", sender, "{code} Confirmed. Ksh{amt}.00 sent to {name} {phone} on {date} at {time}. New M-PESA balance is Ksh{bal}.00. Transaction cost, Ksh{cost}.00.")
    legit("mpesa_paybill", "en", sender, "{code} Confirmed. Ksh{amt}.00 paid to KPLC PREPAID. on {date} at {time}. New M-PESA balance is Ksh{bal}.00. Transaction cost, Ksh0.00.")
    legit("mpesa_airtime", "en", sender, "{code} Confirmed. You bought Ksh{amt}.00 of airtime on {date} at {time}. New M-PESA balance is Ksh{bal}.00.")
    legit("mpesa_withdraw", "en", sender, "{code} Confirmed. on {date} at {time} Withdraw Ksh{amt}.00 from 123456 - AGENT Nairobi. New M-PESA balance is Ksh{bal}.00. Transaction cost, Ksh{cost}.00.")
    legit("mpesa_reversal", "en", sender, "{code} Confirmed. Reversal of transaction {code2} has been successfully reversed on {date} at {time} and Ksh{amt}.00 is credited to your M-PESA account. New M-PESA balance is Ksh{bal}.00.")
    legit("mpesa_fuliza", "en", sender, "Fuliza M-PESA amount is Ksh {amt}.00. Interest charged Ksh 3.00. Total Fuliza M-PESA outstanding amount is Ksh {bal}.00 due on {date}.")
    legit("mpesa_fuliza", "en", sender, "Confirmed. Ksh {amt}.00 from your M-PESA has been used to partially pay your Fuliza M-PESA outstanding amount. Available Fuliza M-PESA limit is Ksh {bal}.00.")
legit("safaricom", "en", "Safaricom", "Dear customer, you have bought {n}MB of data valid for 24 hours. Dial *544# to check your balance.")
legit("safaricom", "en", "Safaricom", "Beware of fraudsters. Safaricom will never ask you for your M-PESA PIN or call you to reverse money. Report fraud to 333.")
legit("safaricom", "en", "Safaricom", "Safaricom: Your M-PESA PIN was changed successfully on {date}. If you did not do this, call 100 immediately.")
legit("safaricom", "sw", "Safaricom", "Mpendwa mteja, umenunua dakika {n} zinazodumu kwa saa 24. Piga *544# kuangalia salio lako.")
for bank in ("EQUITY", "KCB", "COOPBANK", "NCBA"):
    legit("bank_otp", "en", bank, "Your OTP for transaction is {otp}. Do not share this code with anyone, including bank staff. Valid for 5 minutes.")
    legit("bank_otp", "sw", bank, "Nambari yako ya siri ya mara moja ni {otp}. Usimpe mtu yeyote. Inaisha baada ya dakika 5.")
    legit("bank_alert", "en", bank, "{bank}: Your account ending {acct} has been credited with KES {amt}.00 on {date}. Available balance KES {bal}.00.".replace("{bank}", bank))
    legit("bank_alert", "en", bank, "Your account ending {acct} was debited KES {amt}.00 on {date}. Not you? Call the number on your card.")
    legit("bank_warning", "en", bank, "Never share your PIN or password. We will never ask for it by SMS, call or link.")
legit("kra", "en", "KRA", "KRA: Dear taxpayer, your 2025 income tax return is due on 30 June. File on iTax at https://itax.kra.go.ke. Ignore if already filed.")
legit("kra", "sw", "KRA", "KRA: Mpendwa mlipa ushuru, tarehe ya mwisho ya kuwasilisha ritani ya ushuru ni 30 Juni. Tembelea itax.kra.go.ke. Puuza ikiwa umeshawasilisha.")
legit("kra", "en", "KRA", "KRA: Your payment of Ksh {amt}.00 has been received. Thank you for paying your taxes.")
legit("kplc", "en", "KPLC", "KPLC: Token {tok} Units {units} Amount Ksh{amt}.00 on {date}. Thank you for buying electricity.")
legit("kplc", "en", "KPLC", "KPLC: Planned maintenance in your area on {date} from 9 AM to 5 PM. We apologise for the inconvenience.")
legit("delivery", "en", "PARCELS", "Your parcel {parcel} is out for delivery today between 2 PM and 5 PM. Reply STOP to opt out.")
legit("delivery", "en", "PARCELS", "Your order {parcel} has been dispatched. Track it at https://www.dhl.com/track")
legit("delivery", "sw", "PARCELS", "Kifurushi chako {parcel} kiko njiani, kitafika leo kati ya saa 2 na saa 5.")
for t, lang in [("Hey, are we still meeting at 6?", "en"), ("Running late, see you at 7. Save me a seat!", "en"),
                ("Can you pick up the kids today? I will be home by 8.", "en"), ("Happy birthday! Have a great day.", "en"),
                ("Check out this song https://youtu.be/dQw4w9WgXcQ you will love it", "en"),
                ("Habari yako? Nitakuja kesho asubuhi kukuchukua saa mbili.", "sw"), ("Asante sana kwa msaada wako jana.", "sw"),
                ("Tutaonana kanisani Jumapili, usisahau kuleta Biblia.", "sw"), ("Mama anaomba umpigie simu ukipata nafasi.", "sw"),
                ("Msee niko job nitakupigia baadaye, tuko poa.", "sheng"), ("Niaje, tukutane keja saa moja, nimeleta nyama.", "sheng"),
                ("Form ni ngumu leo, tutaongea kesho bro.", "sheng")]:
    legit("chat", lang, "contact", t, hard=False)
for t, lang in [("Can you lend me {amt} till Friday? I will pay you back, promise.", "en"),
                ("Sent you Ksh {amt} for lunch, check your M-Pesa.", "en"),
                ("Nimetuma rent Ksh {amt} kupitia M-Pesa, angalia kama imefika.", "sw"),
                ("Nimekutumia {amt} ya nauli, ukifika nipigie.", "sw"),
                ("Nimekutumia pesa ya shule, angalia M-Pesa yako.", "sw"),
                ("Bro nimekutumia {amt} ya fare, ukifika nicheki.", "sheng")]:
    legit("chat_money", lang, "contact", t, hard=True)

SENDERS = {"promo": ["PROMO", "WINNERS", "INFO", "MPESA_CARE", "SAFARICOM-PROMO", "OFFERS"]}


def _sender(rng: random.Random, kind: str) -> str:
    if kind in ("personal", "contact"):
        return f"254700000{rng.randint(0, 999):03d}"
    if kind == "spoof":
        return rng.choice(["MPESA_CARE", "SAFARICOM-CARE", "M-PESA.", "MPESA HELP", "SAFARICOM1"])
    if kind == "promo":
        return rng.choice(SENDERS["promo"] + [f"254700000{rng.randint(0, 999):03d}"])
    return kind  # a real sender ID such as MPESA, KPLC, EQUITY


def assign_splits(rng: random.Random) -> list[str]:
    """Per (label, campaign): about one in five templates goes to validation, rest to train."""
    groups: dict[tuple, list[int]] = {}
    for i, t in enumerate(S):
        groups.setdefault((t[0], t[1]), []).append(i)
    split = ["train"] * len(S)
    for idxs in groups.values():
        if len(idxs) >= 3:
            for i in rng.sample(idxs, max(1, len(idxs) // 5)):
                split[i] = "val"
    return split


def generate(seed: int = 42, per_template: int = 20) -> list[dict]:
    rng = random.Random(seed)
    splits = assign_splits(rng)
    rows = []
    for ti, (label, campaign, lang, sender_kind, hard, template) in enumerate(S):
        for k in range(per_template):
            slots = _slots(rng)
            rows.append({
                "id": f"syn-{ti:03d}-{k:02d}", "text": template.format(**slots), "sender": _sender(rng, sender_kind),
                "label": label, "campaign": campaign if label == "scam" else "legit", "source": "synthetic",
                "is_hard_negative": str(hard).lower(), "language": lang, "split": splits[ti],
            })
    return rows


def write_csv(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=COLUMNS)
        w.writeheader()
        w.writerows(rows)


def read_csv(path: Path) -> list[dict]:
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


if __name__ == "__main__":
    out = Path(__file__).resolve().parents[3] / "data" / "processed" / "synthetic.csv"
    rows = generate()
    write_csv(out, rows)
    print(f"wrote {len(rows)} synthetic rows to {out}")
