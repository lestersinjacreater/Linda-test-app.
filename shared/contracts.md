# Contracts

The authoritative list of API contracts is section 5 of the root CLAUDE.md.
Any change: update CLAUDE.md section 5, every component that uses the contract, and its tests,
in one commit, with the message prefix `contract:`.

## Normalizer rules (5.9, implemented in ml/src/features/normalize/normalize.py)
1. Lowercase A-Z only (ASCII), so Python and Kotlin agree.
2. Undo leetspeak (3 0 1 4 5 @ $) only inside words: a run of such characters is swapped when letters are on both
   sides, when it is only @/$ touching a letter, or when it is a single character at a word edge. Amounts such as
   `2,500`, `Ksh3,140`, `Ksh500` are left alone. 10-character M-Pesa codes (capitals+digits) are never swapped.
3. `m pesa`, `m.pesa`, `m-pesa` become `mpesa`. Three or more single letters separated by one space, dot, dash
   or underscore are joined (`t u m a` becomes `tuma`).
4. Commas between digits are removed (`2,500` becomes `2500`); every other non `a-z0-9` run becomes one space.

## SimHash (5.1 `fingerprint`, ml/src/features/fingerprint/simhash.py)
64-bit; pieces are the overlapping character 3-grams of the normalised text (counted, not deduplicated);
each piece is hashed with FNV-1a 64 (offset 0xcbf29ce484222325, prime 0x100000001b3) over its ASCII bytes;
bit b of the result is 1 when the sum over pieces of (+count if bit b is set, else -count) is positive.
Text shorter than 3 characters is one piece; empty text gives `0000000000000000`. Output is 16 lowercase hex chars.
