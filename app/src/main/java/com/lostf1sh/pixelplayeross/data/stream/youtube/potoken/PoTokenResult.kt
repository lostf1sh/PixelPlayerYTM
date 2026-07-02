package com.lostf1sh.pixelplayeross.data.stream.youtube.potoken

/**
 * A minted BotGuard PoToken pair.
 *
 * - [playerRequestPoToken] is sent inside the `player` request body
 *   (`serviceIntegrityDimensions.poToken`) so YouTube returns unthrottled streams.
 * - [streamingDataPoToken] is appended to the googlevideo stream URL as `&pot=`, which is
 *   what lifts the `svpuc` per-request throttle (without it the CDN serves only ~1 MB).
 */
class PoTokenResult(
    val playerRequestPoToken: String,
    val streamingDataPoToken: String,
)
