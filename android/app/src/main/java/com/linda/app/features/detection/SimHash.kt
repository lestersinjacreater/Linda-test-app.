package com.linda.app.features.detection

/**
 * 64-bit SimHash of a normalised message. MUST match ml/src/features/fingerprint/simhash.py.
 *
 * Two messages from the same scam campaign share most of their character 3-grams, so their
 * fingerprints differ in only a few bits. The radar uses that to spot a known campaign from a
 * new number without ever seeing the text. A fingerprint cannot be turned back into the message.
 */
object SimHash {
    private const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325 as a signed Long
    private const val FNV_PRIME = 0x100000001b3L

    /** FNV-1a, a very simple 64-bit hash. Long arithmetic wraps around, which is exactly what we want. */
    fun fnv1a64(data: ByteArray): Long {
        var h = FNV_OFFSET
        for (b in data) {
            h = h xor (b.toLong() and 0xff)
            h *= FNV_PRIME
        }
        return h
    }

    /** Overlapping n-grams. Text shorter than n is one piece; empty text has none. */
    fun charNgrams(text: String, n: Int = 3): List<String> {
        if (text.isEmpty()) return emptyList()
        if (text.length < n) return listOf(text)
        return (0..text.length - n).map { text.substring(it, it + n) }
    }

    /** The fingerprint as 16 lowercase hex characters. */
    fun simhash64(normalizedText: String): String {
        val counts = HashMap<String, Int>()
        for (g in charNgrams(normalizedText)) counts[g] = (counts[g] ?: 0) + 1
        val totals = IntArray(64)
        for ((gram, count) in counts) {
            val h = fnv1a64(gram.toByteArray(Charsets.US_ASCII))
            for (bit in 0 until 64) {
                totals[bit] += if ((h ushr bit) and 1L == 1L) count else -count
            }
        }
        var value = 0L
        for (bit in 0 until 64) if (totals[bit] > 0) value = value or (1L shl bit)
        return java.lang.Long.toHexString(value).padStart(16, '0')
    }

    fun hammingDistance(aHex: String, bHex: String): Int =
        java.lang.Long.bitCount(java.lang.Long.parseUnsignedLong(aHex, 16) xor java.lang.Long.parseUnsignedLong(bHex, 16))
}
