from src.features.scoring.metadata import FEATURE_NAMES, metadata_features

VERIFIED = {"MPESA", "M-PESA", "KPLC"}
REAL = "QK7RT2XY9P Confirmed. You have received Ksh2,500.00 from JOHN KAMAU on 12/10/26 at 4:12 PM."


def feats(text, sender):
    return dict(zip(FEATURE_NAMES, metadata_features(text, sender, VERIFIED)))


def test_real_mpesa_is_not_fake():
    f = feats(REAL, "MPESA")
    assert f["mpesa_style"] == 1 and f["sender_verified"] == 1 and f["fake_mpesa"] == 0


def test_same_text_from_a_personal_number_is_fake():
    f = feats(REAL, "254700000005")
    assert f["fake_mpesa"] == 1 and f["sender_personal_number"] == 1 and f["sender_verified"] == 0


def test_same_text_from_a_spoofed_sender_id_is_fake():
    assert feats(REAL, "MPESA_CARE")["fake_mpesa"] == 1


def test_sender_match_ignores_case_and_spaces():
    assert feats(REAL, " mpesa ")["sender_verified"] == 1


def test_pasted_text_without_sender_never_fires_fake_mpesa():
    f = feats(REAL, None)
    assert f["mpesa_style"] == 1 and f["fake_mpesa"] == 0


def test_ordinary_text_is_not_mpesa_style():
    assert feats("Hey, are we still meeting at 6?", "254700000001")["mpesa_style"] == 0
    assert feats("Your OTP is 482913. Confirmed by bank.", "EQUITY")["mpesa_style"] == 0


def test_links_and_shorteners():
    assert feats("pay at http://x.xyz now", "254700000001")["has_link"] == 1
    assert feats("open bit.ly/abc", "254700000001")["has_link"] == 1  # shorteners count even without http
    assert feats("the bit.ly company", "254700000001")["has_link"] == 0
    assert feats("open https://bit.ly/abc", "254700000001")["has_link"] == 1
    assert feats("see itax.kra.go.ke", "KRA")["has_link"] == 0


def test_personal_number_formats():
    for s in ("254712345678", "0712345678", "+254112345678"):
        assert feats("hi", s)["sender_personal_number"] == 1, s
    assert feats("hi", "EQUITY")["sender_personal_number"] == 0
