package com.lostf1sh.pixelplayerytm.data.stream

/**
 * Extracts the JavaScript for the signature-descrambling function and the
 * `n` throttling-parameter function from YouTube's player `base.js`.
 *
 * These regexes track the shapes YouTube currently ships. When playback of
 * WEB_REMIX/TVHTML5 streams starts failing with 403s, the extraction patterns
 * here are the first thing to refresh.
 */
object PlayerBaseJsExtractor {

    private val SIG_FUNCTION_NAME = listOf(
        Regex("""\bm=([a-zA-Z0-9$]{2,})\(decodeURIComponent\(h\.s\)\)"""),
        Regex("""\bc&&\(c=([a-zA-Z0-9$]{2,})\(decodeURIComponent\(c\)\)"""),
        Regex("""(?:\b|[^a-zA-Z0-9$])([a-zA-Z0-9$]{2,})\s*=\s*function\(\s*a\s*\)\s*\{\s*a\s*=\s*a\.split\(\s*""\s*\)"""),
        Regex("""([a-zA-Z0-9$]+)\s*=\s*function\(\s*a\s*\)\s*\{\s*a\s*=\s*a\.split\(\s*""\s*\);[a-zA-Z0-9$]+\."""),
    )

    private val N_FUNCTION_NAME = listOf(
        Regex("""\.get\("n"\)\)&&\([a-zA-Z0-9$]+=([a-zA-Z0-9$]+)(?:\[(\d+)\])?\([a-zA-Z0-9$]+\)"""),
        Regex("""[a-zA-Z0-9$]+="nn"\[\+[a-zA-Z0-9$]+\.[a-zA-Z0-9$]+\],[a-zA-Z0-9$]+=[a-zA-Z0-9$]+\.get\([a-zA-Z0-9$]+\)\)&&\([a-zA-Z0-9$]+=([a-zA-Z0-9$]+)(?:\[(\d+)\])?\([a-zA-Z0-9$]+\)"""),
        Regex("""\bc=([a-zA-Z0-9$]+)(?:\[(\d+)\])?\(c\)"""),
    )

    data class Extraction(
        val sigFunctionName: String,
        val sigFunctionCode: String,
        val nFunctionName: String,
        val nFunctionCode: String,
        val signatureTimestamp: Int?,
    )

    fun extract(baseJs: String): Extraction {
        val sigName = matchFirst(baseJs, SIG_FUNCTION_NAME)
            ?: error("Could not locate signature function name in base.js")
        val sigCode = extractFunctionAndHelpers(baseJs, sigName)

        val nMatch = N_FUNCTION_NAME.firstNotNullOfOrNull { it.find(baseJs) }
            ?: error("Could not locate n function name in base.js")
        var nName = nMatch.groupValues[1]
        // Some players store the function inside an array: var X=[fn]; ...X[0](n)
        val arrayIndex = nMatch.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
        if (arrayIndex != null) {
            nName = resolveArrayReference(baseJs, nName, arrayIndex.toInt()) ?: nName
        }
        val nCode = extractSingleFunction(baseJs, nName)

        return Extraction(
            sigFunctionName = sigName,
            sigFunctionCode = sigCode,
            nFunctionName = nName,
            nFunctionCode = nCode,
            signatureTimestamp = extractSignatureTimestamp(baseJs),
        )
    }

    private fun matchFirst(text: String, patterns: List<Regex>): String? =
        patterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1) }

    fun extractSignatureTimestamp(baseJs: String): Int? =
        Regex("""(?:signatureTimestamp|sts)\s*[:=]\s*(\d+)""")
            .find(baseJs)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Extracts the descrambler function plus the helper object it references.
     * The descrambler is of the form `xy=function(a){a=a.split("");Zx.AA(a,1);...}`
     * where `Zx` is a helper object literal holding the swap/splice/reverse ops.
     */
    private fun extractFunctionAndHelpers(baseJs: String, name: String): String {
        val fn = extractSingleFunction(baseJs, name)
        val helperName = Regex("""[;\{]([a-zA-Z0-9$]{2,})\.[a-zA-Z0-9$]{1,}\(""")
            .find(fn)?.groupValues?.get(1)
        val helper = helperName?.let { extractObjectLiteral(baseJs, it) }
        return listOfNotNull(helper, "var $name=${fn.substringAfter("=")}").joinToString(";\n")
    }

    /** Extracts `name = function(...) {...}` (or `function name(...) {...}`). */
    private fun extractSingleFunction(baseJs: String, name: String): String {
        val escaped = Regex.escape(name)
        val patterns = listOf(
            Regex("""(?:var\s+)?$escaped\s*=\s*function\s*\([^)]*\)\s*\{"""),
            Regex("""function\s+$escaped\s*\([^)]*\)\s*\{"""),
            Regex("""(?:var\s+)?$escaped\s*=\s*\([^)]*\)\s*=>\s*\{"""),
        )
        val match = patterns.firstNotNullOfOrNull { it.find(baseJs) }
            ?: error("Function $name not found in base.js")
        val bodyStart = baseJs.indexOf('{', match.range.first)
        val body = extractBalanced(baseJs, bodyStart)
        val header = "var $name=function" +
            baseJs.substring(match.range.first, bodyStart).substringAfter("function").substringAfter("=>").let {
                // Normalize to a function() form with the original arg list.
                val args = Regex("""\(([^)]*)\)""").find(match.value)?.groupValues?.get(1) ?: "a"
                "($args)"
            }
        return "$header$body"
    }

    private fun extractObjectLiteral(baseJs: String, name: String): String {
        val escaped = Regex.escape(name)
        val match = Regex("""var\s+$escaped\s*=\s*\{""").find(baseJs)
            ?: Regex("""$escaped\s*=\s*\{""").find(baseJs)
            ?: error("Helper object $name not found in base.js")
        val braceStart = baseJs.indexOf('{', match.range.first)
        val body = extractBalanced(baseJs, braceStart)
        return "var $name=$body"
    }

    private fun resolveArrayReference(baseJs: String, arrayName: String, index: Int): String? {
        val escaped = Regex.escape(arrayName)
        val match = Regex("""var\s+$escaped\s*=\s*\[([^\]]*)\]""").find(baseJs) ?: return null
        return match.groupValues[1].split(",").getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Given the index of an opening brace/bracket, returns the balanced span including it. */
    private fun extractBalanced(text: String, openIndex: Int): String {
        val open = text[openIndex]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var i = openIndex
        var inString: Char? = null
        var escaped = false
        while (i < text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString != null -> escaped = true
                inString != null -> if (c == inString) inString = null
                c == '"' || c == '\'' || c == '`' -> inString = c
                c == open -> depth++
                c == close -> {
                    depth--
                    if (depth == 0) return text.substring(openIndex, i + 1)
                }
            }
            i++
        }
        error("Unbalanced braces starting at $openIndex")
    }
}
