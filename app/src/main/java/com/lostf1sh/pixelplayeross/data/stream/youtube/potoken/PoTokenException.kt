package com.lostf1sh.pixelplayeross.data.stream.youtube.potoken

class PoTokenException(message: String) : Exception(message)

/** Thrown when the system-provided WebView is broken (e.g. a JS SyntaxError on load). */
class BadWebViewException(message: String) : Exception(message)

fun buildExceptionForJsError(error: String): Exception =
    if (error.contains("SyntaxError")) BadWebViewException(error) else PoTokenException(error)
