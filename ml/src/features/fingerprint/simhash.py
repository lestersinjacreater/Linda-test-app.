"""64-bit SimHash of a normalised message (root CLAUDE.md 5.1).

Two messages from the same scam campaign share most of their character 3-grams, so
their fingerprints differ in only a few bits (small Hamming distance). The radar uses
that to recognise a new number sending a known campaign, without ever seeing the text.
The Kotlin version MUST match exactly.

Steps: split the text into overlapping 3-character pieces; hash each piece to 64 bits
(FNV-1a, simple and identical in every language); for each of the 64 bit positions add
+1 for every piece whose bit is 1 and -1 when it is 0; the fingerprint bit is 1 when
the total is positive.
"""
from collections import Counter

_FNV_OFFSET = 0xCBF29CE484222325
_FNV_PRIME = 0x100000001B3
_MASK = 0xFFFFFFFFFFFFFFFF


def fnv1a_64(data: bytes) -> int:
    h = _FNV_OFFSET
    for b in data:
        h ^= b
        h = (h * _FNV_PRIME) & _MASK
    return h


def char_ngrams(text: str, n: int = 3) -> list[str]:
    """Overlapping n-grams. Text shorter than n is one piece; empty text has none."""
    if not text:
        return []
    if len(text) < n:
        return [text]
    return [text[i : i + n] for i in range(len(text) - n + 1)]


def simhash64(normalized_text: str) -> str:
    """Return the fingerprint as 16 lowercase hex characters."""
    counts = Counter(char_ngrams(normalized_text))
    totals = [0] * 64
    for gram, count in counts.items():
        h = fnv1a_64(gram.encode("ascii"))
        for bit in range(64):
            totals[bit] += count if (h >> bit) & 1 else -count
    value = 0
    for bit in range(64):
        if totals[bit] > 0:
            value |= 1 << bit
    return f"{value:016x}"


def hamming_distance(a_hex: str, b_hex: str) -> int:
    return bin(int(a_hex, 16) ^ int(b_hex, 16)).count("1")
