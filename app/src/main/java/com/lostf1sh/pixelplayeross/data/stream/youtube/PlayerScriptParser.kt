package com.lostf1sh.pixelplayeross.data.stream.youtube

/**
 * Pulls the two functions we need out of YouTube's player `base.js`:
 *
 *  1. the **signature** descrambler — reorders the `s` param of a `signatureCipher`
 *     stream into a valid `sig`, and
 *  2. the **n** transform — rewrites the `n` throttling param so the CDN serves at
 *     full speed instead of throttling to ~50 KB/s.
 *
 * These are pure string ops against obfuscated JS, so the regexes track whatever shape
 * YouTube currently ships. When streams start 403-ing or downloading at a crawl, the
 * patterns here are the first thing to refresh (compare against yt-dlp / NewPipe).
 */
internal object PlayerScriptParser {

    data class Functions(
        val signatureName: String,
        val signatureCode: String,
        val nName: String,
        val nCode: String,
        val signatureTimestamp: Int?,
    )

    private val SIGNATURE_NAME_PATTERNS = listOf(
        Regex("""\bm=([a-zA-Z0-9$]{2,})\(decodeURIComponent\(h\.s\)\)"""),
        Regex("""\bc&&\(c=([a-zA-Z0-9$]{2,})\(decodeURIComponent\(c\)\)"""),
        Regex("""(?:\b|[^a-zA-Z0-9$])([a-zA-Z0-9$]{2,})\s*=\s*function\(\s*a\s*\)\s*\{\s*a\s*=\s*a\.split\(\s*""\s*\)"""),
        Regex("""([a-zA-Z0-9$]+)\s*=\s*function\(\s*a\s*\)\s*\{\s*a\s*=\s*a\.split\(\s*""\s*\);[a-zA-Z0-9$]+\."""),
    )

    private val N_NAME_PATTERNS = listOf(
        Regex("""\.get\("n"\)\)&&\([a-zA-Z0-9$]+=([a-zA-Z0-9$]+)(?:\[(\d+)\])?\([a-zA-Z0-9$]+\)"""),
        Regex("""[a-zA-Z0-9$]+="nn"\[\+[a-zA-Z0-9$]+\.[a-zA-Z0-9$]+\],[a-zA-Z0-9$]+=[a-zA-Z0-9$]+\.get\([a-zA-Z0-9$]+\)\)&&\([a-zA-Z0-9$]+=([a-zA-Z0-9$]+)(?:\[(\d+)\])?\([a-zA-Z0-9$]+\)"""),
        Regex("""\bc=([a-zA-Z0-9$]+)(?:\[(\d+)\])?\(c\)"""),
    )

    private val TIMESTAMP_PATTERN = Regex("""(?:signatureTimestamp|sts)\s*[:=]\s*(\d+)""")
    private val HELPER_REF_PATTERN = Regex("""[;{]([a-zA-Z0-9$]{2,})\.[a-zA-Z0-9$]{1,}\(""")

    fun parse(baseJs: String): Functions {
        val signatureName = SIGNATURE_NAME_PATTERNS
            .firstNotNullOfOrNull { it.find(baseJs)?.groupValues?.getOrNull(1)?.takeIf(String::isNotEmpty) }
            ?: error("signature function name not found in base.js")

        val nMatch = N_NAME_PATTERNS.firstNotNullOfOrNull { it.find(baseJs) }
            ?: error("n function name not found in base.js")
        var nName = nMatch.groupValues[1]
        nMatch.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { idx ->
            resolveArrayEntry(baseJs, nName, idx.toInt())?.let { nName = it }
        }

        return Functions(
            signatureName = signatureName,
            signatureCode = extractSignatureWithHelper(baseJs, signatureName),
            nName = nName,
            nCode = extractFunction(baseJs, nName),
            signatureTimestamp = TIMESTAMP_PATTERN.find(baseJs)?.groupValues?.get(1)?.toIntOrNull(),
        )
    }

    /**
     * The descrambler is `xy=function(a){a=a.split("");Zx.AA(a,1);...}` where `Zx` is an
     * object literal holding the reverse/swap/splice ops — both must be shipped to the JS VM.
     */
    private fun extractSignatureWithHelper(baseJs: String, name: String): String {
        val fn = extractFunction(baseJs, name)
        val helper = HELPER_REF_PATTERN.find(fn)?.groupValues?.get(1)
            ?.let { extractObjectLiteral(baseJs, it) }
        return listOfNotNull(helper, fn).joinToString(";\n")
    }

    /** Extract `name=function(args){...}` (or `function name(...){...}`) as `var name=function(args){...}`. */
    private fun extractFunction(baseJs: String, name: String): String {
        val escaped = Regex.escape(name)
        val header = listOf(
            Regex("""(?:var\s+)?$escaped\s*=\s*function\s*\(([^)]*)\)\s*\{"""),
            Regex("""function\s+$escaped\s*\(([^)]*)\)\s*\{"""),
            Regex("""(?:var\s+)?$escaped\s*=\s*\(([^)]*)\)\s*=>\s*\{"""),
        ).firstNotNullOfOrNull { it.find(baseJs) }
            ?: error("function $name not found in base.js")

        val args = header.groupValues.getOrNull(1).orEmpty()
        val braceStart = baseJs.indexOf('{', header.range.first)
        val body = extractBalanced(baseJs, braceStart)
        return "var $name=function($args)$body"
    }

    private fun extractObjectLiteral(baseJs: String, name: String): String {
        val escaped = Regex.escape(name)
        val match = Regex("""(?:var\s+)?$escaped\s*=\s*\{""").find(baseJs)
            ?: error("helper object $name not found in base.js")
        val braceStart = baseJs.indexOf('{', match.range.first)
        return "var $name=${extractBalanced(baseJs, braceStart)}"
    }

    private fun resolveArrayEntry(baseJs: String, arrayName: String, index: Int): String? {
        val escaped = Regex.escape(arrayName)
        val match = Regex("""var\s+$escaped\s*=\s*\[([^\]]*)\]""").find(baseJs) ?: return null
        return match.groupValues[1].split(',').getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** From an opening brace/bracket, return the balanced span (string- and escape-aware). */
    private fun extractBalanced(text: String, openIndex: Int): String {
        val open = text[openIndex]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var quote: Char? = null
        var escaped = false
        var i = openIndex
        while (i < text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && quote != null -> escaped = true
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' || c == '`' -> quote = c
                c == open -> depth++
                c == close -> {
                    depth--
                    if (depth == 0) return text.substring(openIndex, i + 1)
                }
            }
            i++
        }
        error("unbalanced braces from index $openIndex")
    }
}
