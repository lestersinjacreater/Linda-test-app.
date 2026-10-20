"""Customer-facing labels. Short, plain, in English and Swahili, and never the fraud team's evidence."""


def labels(status: str, reports: int, name_en: str = "", name_sw: str = "") -> tuple[str, str]:
    if status == "verified":
        return name_en, name_sw
    n = max(reports, 1)
    people_en = "1 person" if n == 1 else f"{n} people"
    if status == "confirmed":
        return f"Reported for fraud by {people_en}", f"Imeripotiwa kwa ulaghai na watu {n}"
    if status == "suspected":
        return f"Suspected scam, reported by {people_en}", f"Inashukiwa kuwa ulaghai, imeripotiwa na watu {n}"
    return f"Reported to Safaricom by {people_en}", f"Imeripotiwa kwa Safaricom na watu {n}"  # "reported"
