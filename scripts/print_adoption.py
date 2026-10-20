"""Prints one line per adoption run, or the odds table. Used by `make demo-adoption` (reads JSON on stdin)."""
import json
import sys


def pct(x):
    return "-" if x is None else f"{x}%"


def main() -> None:
    data = json.load(sys.stdin)
    if "levels" in data:  # GET /adoption/odds
        print(f"\nAverage over {data['trials']} replays of a {data['recipients']}-person blast"
              f" (network acts after {data['min_reports']} reports). Assumes: {data['assumes']}.")
        for lv in data["levels"]:
            print(f"  {lv['adoption_pct']:>4}%: confirmed in {lv['confirmed_pct']}% of blasts"
                  f" (binomial {lv['confirmed_pct_binomial']}%), warned before reading {lv['warned_before_read_pct']}%"
                  f" (Linda alone: {lv['warned_before_read_pct_linda_only']}%)")
        return
    m = data["metrics"]
    confirmed = "never confirmed" if m["confirmed_after_s"] is None else f"confirmed after {m['confirmed_after_s']} s"
    print(f"{m['adoption_pct']:>4}% adoption: {m['linda_recipients']} of {m['delivered']} recipients had Linda, {confirmed},"
          f" warned before reading {pct(m['warned_before_read_pct'])}, basic phones {pct(m['feature_phones_warned_before_read_pct'])}")


if __name__ == "__main__":
    main()
