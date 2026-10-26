"""The donated-inbox builder: scrubbing, exclusions and capping. Made-up messages only: no real inbox data in tests."""
import csv
import re
import subprocess

from src.features.dataset import team_inbox as T

CODE = re.compile(r"(?<![A-Za-z0-9])(?=[A-Z0-9]{10}(?![A-Za-z0-9]))(?=[A-Z0-9]*[A-Z])(?=[A-Z0-9]*[0-9])[A-Z0-9]{10}")


def test_names_after_money_words_are_masked_in_capitals_and_title_case():
    assert "JOHN DOE" in T.scrub("SJ34K9L2QW Confirmed. You have received Ksh500.00 from Maria Wanjiku 0711111111 on 12/10/26.")
    assert "Wanjiku" not in T.scrub("Ksh 300 sent to MARIA WANJIKU on 07/09/25.")
    assert "Otieno" not in T.scrub("Umepokea Ksh2,500.00 kutoka kwa Peter Otieno tarehe 11/10/26").title()
    assert "Kamau" not in T.scrub("Brian Kamau Mwangi has sent you KES. 1000.00 to your MPESA.")


def test_brand_words_survive():
    s = T.scrub("Ksh 10 paid to Lipa Na Mpesa via Airtel Money. Join M-Shwari today.")
    assert "Lipa Na Mpesa" in s and "Airtel Money" in s and "M-Shwari" in s


def test_the_donors_family_names_never_survive():
    # Made-up names: the real ones live in the private, git-ignored donor_names.txt, never in the code or the tests.
    family = T.names_pattern(["Amani", "Wekesa", "Nafula"])
    for name in ("Amani", "AMANI", "Wekesa", "Nafula"):
        assert name.lower() not in T.scrub(f"Dear {name}, your bundle is ready. Hi {name}!", family).lower()
    assert "wekesa" not in T.scrub("You have received KES 50 into your AMANI WEKESA - 305097 Till", family).lower()


def test_every_donation_is_read(tmp_path, monkeypatch):
    for name in ("team_inbox.csv", "team_inbox_2.csv", "other.csv"):
        (tmp_path / name).write_text("x", encoding="utf-8")
    monkeypatch.setattr(T, "RAW_DIR", tmp_path)
    assert [p.name for p in T.donations()] == ["team_inbox.csv", "team_inbox_2.csv"]


def test_numbers_codes_markers_and_emails_are_replaced():
    s = T.scrub("SJ34K9L2QW Confirmed. Call 0712345678 or <PHONE_ab12cd> or mail jane.doe@gmail.com. Code <OTP>")
    assert "SJ34K9L2QW" not in s and CODE.search(s), "a code of the same shape replaces the real one"
    assert "0712345678" not in s and "<PHONE" not in s and "<OTP>" not in s
    assert "gmail.com" not in s


def test_fake_codes_keep_the_m_pesa_shape():
    for real in ("SJ34K9L2QW", "TB20EY6722", "UFR0J9D7SW"):
        fake = T.fake_code(real)
        assert fake != real and CODE.fullmatch(fake), fake


def _write(path, header, rows):
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(rows)


def test_build_drops_staff_unsure_and_excluded_and_caps_repeats(tmp_path):
    raw, labels = tmp_path / "inbox.csv", tmp_path / "labels.csv"
    texts = [f"Dear customer, your data balance is {n}MB. Dial *544# to buy a bundle." for n in (40, 45, 50, 55)]
    rows = [["d1", "SAF_Balance", "company", t, "2026-01-01T00:00:00Z", ""] for t in texts]
    rows += [["d1", "StaffUpdate", "company", "Team meeting at 10am in the boardroom.", "2026-01-01T00:00:00Z", ""],
             ["d1", "P_abc123", "personal", "Dear tenant, rent is now paid to Till 5757617 only.", "2026-01-01T00:00:00Z", ""],
             ["d1", "P_abc124", "personal", "Tuma kwa hii number", "2026-01-01T00:00:00Z", ""],
             ["d1", "KRACare", "company", "Your password is: Abc123xyz", "2026-01-01T00:00:00Z", ""]]
    _write(raw, ["donor_id", "sender_id", "sender_type", "text", "received_at", "label"], rows)
    lab = {T.raw_id(t): "legit" for t in texts}
    lab[T.raw_id("Dear tenant, rent is now paid to Till 5757617 only.")] = "scam"
    lab[T.raw_id("Tuma kwa hii number")] = "unsure"
    lab[T.raw_id("Your password is: Abc123xyz")] = "exclude"
    _write(labels, ["raw_id", "label"], sorted(lab.items()))
    out, stats = T.build(raw, labels)
    assert stats["staff"] == 1 and stats["unsure"] == 1 and stats["excluded"] == 1
    assert sum(1 for r in out if r["sender"] == "SAF_Balance") == T.MAX_PER_GROUP  # four near-copies, capped
    scam = [r for r in out if r["label"] == "scam"]
    assert len(scam) == 1 and scam[0]["sender"] == T.PERSONAL_SENDER  # hashed personal ids are never kept
    assert not any("password" in r["text"].lower() or "meeting" in r["text"].lower() for r in out)


def test_without_the_private_files_the_pipeline_still_runs(tmp_path):
    rows, stats = T.build(tmp_path / "missing.csv", tmp_path / "missing-labels.csv")
    assert rows == [] and stats["read"] == 0


def test_private_files_are_git_ignored():
    for p in ("ml/data/raw/team_inbox.csv", "ml/data/raw/team_inbox_2.csv", "ml/data/labels/private/team_inbox_labels.csv",
              "ml/data/labels/private/donor_names.txt",
              "ml/data/processed/team_collected.csv"):
        assert subprocess.run(["git", "check-ignore", "-q", p], cwd=T.ROOT).returncode == 0, p
