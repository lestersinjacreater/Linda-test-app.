"""Checks a running Linda deployment from the outside, the way the audience and the phones see it.

    python scripts/smoke_prod.py https://linda.example.com presenter 'the-presenter-password'
    (or: make prod-check BASE=https://linda.example.com)

It prints PASS or FAIL for each check and exits with 1 if anything failed. It plays one scam blast on
the live simulator, so do not run it during a presentation. Only needs the Python standard library.
"""
import base64
import json
import sys
import urllib.error
import urllib.request


def call(url: str, method: str = "GET", user: str | None = None, password: str | None = None, timeout: float = 120.0):
    """Returns (status code, body text). A 4xx/5xx is a result here, not an exception."""
    req = urllib.request.Request(url, method=method, data=b"" if method == "POST" else None)
    if user is not None:
        token = base64.b64encode(f"{user}:{password}".encode()).decode()
        req.add_header("Authorization", f"Basic {token}")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except Exception as e:  # DNS, refused, TLS...
        return 0, str(e)


def main() -> int:
    if len(sys.argv) < 4:
        print(__doc__)
        return 2
    base, user, password = sys.argv[1].rstrip("/"), sys.argv[2], sys.argv[3]
    failures = 0

    def check(name: str, ok: bool, detail: str = "") -> None:
        nonlocal failures
        print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f"   ({detail})" if detail and not ok else ""))
        failures += 0 if ok else 1

    # --- the public side -----------------------------------------------------------------------------------------
    code, body = call(f"{base}/")
    check("dashboard page loads", code == 200 and "Linda" in body, f"HTTP {code}")
    code, body = call(f"{base}/radar/health")
    check("radar is up (/radar/health)", code == 200, f"HTTP {code} {body[:80]}")
    code, body = call(f"{base}/telco/health")
    check("simulator is up (/telco/health)", code == 200, f"HTTP {code} {body[:80]}")
    code, body = call(f"{base}/telco/population")
    check("300 phones on the map (/telco/population)", code == 200 and len(json.loads(body)) == 300, f"HTTP {code}")

    # --- what must NOT be reachable from the internet --------------------------------------------------------------
    for path in ("/telco/network/confirm", "/telco/deliveries?sender=x", "/telco/history"):
        code, _ = call(f"{base}{path}")
        check(f"private simulator path is closed: {path.split('?')[0]}", code in (404, 405), f"HTTP {code}")
    code, _ = call(f"{base}/telco/scenarios/blast", "POST")
    check("starting a blast without the password is refused", code == 401, f"HTTP {code}")
    code, _ = call(f"{base}/telco/reset", "POST", user, "definitely-the-wrong-password")
    check("starting with a wrong password is refused", code == 401, f"HTTP {code}")

    # --- one real end-to-end run -------------------------------------------------------------------------------------
    code, body = call(f"{base}/telco/scenarios/blast?wait=true", "POST", user, password)
    check("presenter can start a blast", code == 200, f"HTTP {code} {body[:120]}")
    if code == 200:
        result = json.loads(body)
        metrics, sender = result["metrics"], result["senders"][0]
        check("radar confirmed the scam number", metrics["confirmed_after_s"] is not None, "trust settings? see docs/DEPLOY.md")
        check("most recipients warned before reading", (metrics["warned_before_read_pct"] or 0) > 90, str(metrics["warned_before_read_pct"]))
        code, body = call(f"{base}/radar/v1/numbers/{sender}/risk")
        check("the radar reports the number as confirmed (what the Android app and USSD will see)",
              code == 200 and json.loads(body)["status"] == "confirmed", f"HTTP {code} {body[:80]}")
        code, body = call(f"{base}/radar/v1/blocklist")
        check("the number is on the blocklist the phones sync",
              code == 200 and sender in [b["msisdn"] for b in json.loads(body)["added"]], f"HTTP {code}")
        print(f"\nheadline: delivered {metrics['delivered']}, warned before reading {metrics['warned_before_read_pct']}%, "
              f"first catch {metrics['first_detection_after_s']}s, confirmed {metrics['confirmed_after_s']}s")

    print("\nALL CHECKS PASSED" if failures == 0 else f"\n{failures} CHECK(S) FAILED")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
