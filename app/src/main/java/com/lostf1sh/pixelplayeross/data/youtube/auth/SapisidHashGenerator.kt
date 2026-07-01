package com.lostf1sh.pixelplayeross.data.youtube.auth

import java.security.MessageDigest

/**
 * Builds the `Authorization: SAPISIDHASH …` header that Google's web APIs require for
 * cookie-authenticated requests. The hash is `SHA1(timestamp + " " + SAPISID + " " + origin)`.
 */
object SapisidHashGenerator {

    private const val ORIGIN = "https://music.youtube.com"

    fun generate(sapisid: String, epochSeconds: Long): String {
        val input = "$epochSeconds $sapisid $ORIGIN"
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${epochSeconds}_$hex"
    }
}
