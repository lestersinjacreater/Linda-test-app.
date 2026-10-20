package com.linda.app.core.util

import java.security.MessageDigest

/**
 * Phone-number hashing shared with the gateway (root CLAUDE.md 5.10): lowercase hex SHA-256 of
 * "2547XXXXXXXX". shared/hash-vectors.json proves Kotlin and Python agree.
 * Flagged numbers are kept and looked up as hashes, so the phone never holds or sends a list of numbers.
 */
object PhoneHash {
    const val PREFIX_LENGTH = 5

    fun of(msisdn: String): String =
        MessageDigest.getInstance("SHA-256").digest(msisdn.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun prefixOf(msisdn: String): String = of(msisdn).take(PREFIX_LENGTH)
}
