package com.example.trackly.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** The app-lock PIN is never stored: only "salt:sha256(salt + pin)". */
object PinHasher {
    const val PIN_LENGTH = 4

    fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String, salt: String = newSalt()): String = "$salt:${digest(salt, pin)}"

    fun verify(pin: String, stored: String?): Boolean {
        if (stored == null) return false
        val parts = stored.split(':', limit = 2)
        if (parts.size != 2) return false
        return MessageDigest.isEqual(digest(parts[0], pin).toByteArray(), parts[1].toByteArray())
    }

    private fun newSalt(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Base64.getEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun digest(salt: String, pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$salt$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
