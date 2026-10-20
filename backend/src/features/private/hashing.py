"""Phone-number hashing shared with the app (root CLAUDE.md 5.10, 5.11).

hash = lowercase hex SHA-256 of the number written as 2547XXXXXXXX (UTF-8). The Kotlin app MUST
produce the same hash; shared/hash-vectors.json proves it.

Honest limit (docs/EXPLAINED.md): Kenyan mobile numbers are a small space, so a hash can be reversed by
trying every number. Hashing stops casual reading; the real protection is that phones never download the
full list (prefix lookups), the offline list is short and recent, and lookups are rate-limited.
"""
import hashlib
from functools import lru_cache

PREFIX_LENGTH = 5  # 16^5 = about a million buckets; each prefix covers about a thousand possible numbers


@lru_cache(maxsize=100_000)
def number_hash(msisdn: str) -> str:
    return hashlib.sha256(msisdn.encode("utf-8")).hexdigest()
