package com.hanif.textscanner.util

/**
 * Post-processor for ML Kit OCR output on Bengali text.
 * Fixes common mistakes that occur when using Devanagari model for Bengali.
 */
object BengaliTextFixer {

    fun fix(raw: String): String {
        var text = raw

        // ── 1. Remove watermarks & noise ─────────────────────────────────
        val noisePatterns = listOf(
            Regex("snờwDdate", RegexOption.IGNORE_CASE),
            Regex("snowDdate", RegexOption.IGNORE_CASE),
            Regex("Naim'?s?\\s*Job\\s*[Aa]nd\\s*Update", RegexOption.IGNORE_CASE),
            Regex("Naim'?s?\\s*oaN\\s*Uodats?", RegexOption.IGNORE_CASE),
            Regex("kwalmrs?\\.[^\\s]*", RegexOption.IGNORE_CASE),
            Regex("www\\.[^\\s]+", RegexOption.IGNORE_CASE),
        )
        noisePatterns.forEach { text = it.replace(text, "") }

        // ── 2. Fix য → য়  (most common Bengali OCR mistake) ─────────────
        // ML Kit often drops the dot under য making য় appear as য
        val yaToYa = listOf(
            "হওযা" to "হওয়া",
            "যাওযা" to "যাওয়া",
            "পাওযা" to "পাওয়া",
            "চাওযা" to "চাওয়া",
            "নাওযা" to "নাওয়া",
            "দেওযা" to "দেওয়া",
            "নেওযা" to "নেওয়া",
            "খাওযা" to "খাওয়া",
            "হযে" to "হয়ে",
            "করযে" to "করয়ে",
            "নিযে" to "নিয়ে",
            "দিযে" to "দিয়ে",
            "গিযে" to "গিয়ে",
            "আসিযে" to "আসিয়ে",
            "চালিযে" to "চালিয়ে",
            "মিলিযে" to "মিলিয়ে",
            "লাগিযে" to "লাগিয়ে",
            "রাখিযে" to "রাখিয়ে",
            "করিযে" to "করিয়ে",
            "আযোজন" to "আয়োজন",
            "কবুলিযত" to "কবুলিয়ত",
            "বিধায " to "বিধায় ",
            "প্রদায " to "প্রদায় ",
            "আদায কর" to "আদায় কর",
            "আদায়কর" to "আদায় কর",
            "ব্যয ভার" to "ব্যয় ভার",
            "ব্যয কর" to "ব্যয় কর",
            "দায ভার" to "দায় ভার",
            "জলবাযু" to "জলবায়ু",
            "বায়ু" to "বায়ু",
        )
        yaToYa.forEach { (wrong, right) ->
            text = text.replace(wrong, right)
        }

        // ── 3. Fix হয / বলা হয at line end → হয় / বলা হয় ───────────────
        text = Regex("হয([।\\.\\n\\s])").replace(text) { m ->
            "হয়${m.groupValues[1]}"
        }
        text = Regex("যায([।\\.\\n\\s])").replace(text) { m ->
            "যায়${m.groupValues[1]}"
        }
        text = Regex("থাকায([।\\.\\n\\s])").replace(text) { m ->
            "থাকায়${m.groupValues[1]}"
        }
        text = Regex("জানায([।\\.\\n\\s])").replace(text) { m ->
            "জানায়${m.groupValues[1]}"
        }

        // ── 4. Fix ভূমিকর → ভূমি কর ──────────────────────────────────────
        text = text.replace("ভূমিকর", "ভূমি কর")
        text = text.replace("ভুমিকর", "ভূমি কর")

        // ── 5. Common word fixes ──────────────────────────────────────────
        val wordFixes = listOf(
            "কিন্তওযার" to "কিস্তোয়ার",
            "কিন্তোযার" to "কিস্তোয়ার",
            "কিস্তোযার" to "কিস্তোয়ার",
            "খানাপুরি" to "খানাপুরি",
            "প্যস্তি" to "পয়স্তি",
            "পযস্তি" to "পয়স্তি",
            "মাঠপর্যায" to "মাঠপর্যায়",
            "পর্যায " to "পর্যায় ",
            "অধিভুক্ত" to "অধিভুক্ত",
            "ক্যাডষ্টাল" to "ক্যাডাস্ট্রাল",
            "ক্যাডস্টাল" to "ক্যাডাস্ট্রাল",
        )
        wordFixes.forEach { (wrong, right) ->
            text = text.replace(wrong, right)
        }

        // ── 6. Clean up extra whitespace ─────────────────────────────────
        text = Regex("[ \\t]{2,}").replace(text, " ")
        text = Regex("\\n{4,}").replace(text, "\n\n\n")
        text = text.lines().joinToString("\n") { it.trimEnd() }

        return text.trim()
    }
}
