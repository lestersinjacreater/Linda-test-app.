"""Disguises messages the way scammers do, so the model learns to see through it.

Used on training data (and, separately, to build the robustness test set). Seeded, so runs repeat.
"""
import random

_LEET = {"e": "3", "o": "0", "i": "1", "a": "4", "s": "5"}
_EMOJI = ["🎉", "💰", "🎁", "🔥", "✅", "📱"]


def leetspeak(text: str, rng: random.Random, rate: float = 0.35) -> str:
    out = []
    for ch in text:
        low = ch.lower()
        if low in _LEET and rng.random() < rate:
            out.append("@" if low == "a" and rng.random() < 0.3 else _LEET[low])
        else:
            out.append(ch)
    return "".join(out)


def space_out_words(text: str, rng: random.Random, rate: float = 0.25) -> str:
    words = []
    for w in text.split(" "):
        words.append(" ".join(w) if len(w) >= 4 and w.isalpha() and rng.random() < rate else w)
    return " ".join(words)


def emoji_noise(text: str, rng: random.Random) -> str:
    return f"{rng.choice(_EMOJI)}{rng.choice(_EMOJI)} {text} {rng.choice(_EMOJI)}"


def obfuscate(text: str, rng: random.Random) -> str:
    """Apply one or two random disguises."""
    tricks = rng.sample([leetspeak, space_out_words, emoji_noise], k=rng.choice([1, 2]))
    for trick in tricks:
        text = trick(text, rng)
    return text
