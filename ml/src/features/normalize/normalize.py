"""Text normaliser (root CLAUDE.md 5.9, android/CLAUDE.md F1).

Scammers disguise words ("K1m@kosa", "M-P3SA", "t u m a"). This module undoes the
disguises so the model and the rules see plain words. The Kotlin version in
android/ MUST give identical output; shared/test-vectors.json proves it.

Only plain ASCII is handled on purpose: Python and Kotlin disagree about how to
lowercase exotic Unicode, and Kenyan SMS scams use ASCII letters. Anything else
(emoji, accents) becomes a space.
"""
import re

NORMALIZER_VERSION = "1"

# Characters scammers swap for letters. Only these are ever swapped back.
LEET_MAP = {"3": "e", "0": "o", "1": "i", "4": "a", "5": "s", "@": "a", "$": "s"}
LEET_CHARS = "".join(LEET_MAP)  # "30145@$"

# An M-Pesa transaction code: exactly 10 capitals/digits with at least one of each ("QK7RT2XY9P").
# Its digits are real digits, not disguised letters, so leetspeak must leave it alone.
_TRANSACTION_CODE_RE = re.compile(
    r"(?<![A-Za-z0-9])(?=[A-Z0-9]{10}(?![A-Za-z0-9]))(?=[A-Z0-9]*[A-Z])(?=[A-Z0-9]*[0-9])[A-Z0-9]{10}"
)
# "m pesa", "m.pesa", "m-p3sa" (after leet is undone) all become "mpesa".
_MPESA_RE = re.compile(r"(?<![a-z])m[ ._-]*p[ ._-]*e[ ._-]*s[ ._-]*a(?![a-z])")
# Three or more single letters separated by one space/dot/dash: "t u m a" -> "tuma".
_SPACED_LETTERS_RE = re.compile(r"(?<![a-z])[a-z](?:[ ._-][a-z]){2,}(?![a-z])")
_SPACED_SEPARATOR_RE = re.compile(r"[ ._-]")
# The comma inside an amount: "2,500" -> "2500".
_AMOUNT_COMMA_RE = re.compile(r"(?<=[0-9]),(?=[0-9])")
_NOT_ALNUM_RE = re.compile(r"[^a-z0-9]+")


def _ascii_lower(text: str) -> str:
    """Lowercase A-Z only, so Python and Kotlin agree on every input."""
    return "".join(chr(ord(c) + 32) if "A" <= c <= "Z" else c for c in text)


def _is_letter(c: str) -> bool:
    return "a" <= c <= "z"


def undo_leetspeak(s: str, protected: list[bool] | None = None) -> str:
    """Swap look-alike symbols back to letters, but only when they sit inside a word.

    A "run" is a stretch of consecutive swappable characters. A run is swapped when:
    - it has a letter on BOTH sides ("m-p3sa", "k1m@kosa", "c0nf1rmed"), or
    - it is only @/$ symbols and touches a letter on either side ("$ave"), or
    - it is ONE character at the edge of a word (whitespace, end of text, or sentence punctuation) ("tum4 ").
    Everything else is left alone, so amounts survive: "2,500", "Ksh3,140", "Ksh500".
    Characters flagged in `protected` (transaction codes) are never swapped.
    """
    out = []
    n = len(s)
    if protected is None:
        protected = [False] * n

    def swappable(k: int) -> bool:
        return s[k] in LEET_CHARS and not protected[k]

    i = 0
    while i < n:
        if not swappable(i):
            out.append(s[i])
            i += 1
            continue
        j = i
        while j < n and swappable(j):
            j += 1
        run = s[i:j]
        left = i > 0 and _is_letter(s[i - 1])
        right = j < n and _is_letter(s[j])
        symbols_only = all(c in "@$" for c in run)
        left_edge = i == 0 or s[i - 1] in " \t\n"
        right_edge = j == n or s[j] in " \t\n!?;:" or (s[j] == "." and not (j + 1 < n and s[j + 1] in "0123456789"))
        swap = (
            (left and right)
            or (symbols_only and (left or right))
            or (len(run) == 1 and ((left and right_edge) or (right and left_edge)))
        )
        out.append("".join(LEET_MAP[c] for c in run) if swap else run)
        i = j
    return "".join(out)


def normalize(text: str) -> str:
    protected = [False] * len(text)
    for m in _TRANSACTION_CODE_RE.finditer(text):
        for k in range(m.start(), m.end()):
            protected[k] = True
    s = _ascii_lower(text)  # same length as `text`, so `protected` still lines up
    s = undo_leetspeak(s, protected)
    s = _MPESA_RE.sub("mpesa", s)
    s = _SPACED_LETTERS_RE.sub(lambda m: _SPACED_SEPARATOR_RE.sub("", m.group()), s)
    s = _AMOUNT_COMMA_RE.sub("", s)
    s = _NOT_ALNUM_RE.sub(" ", s)
    return s.strip()
