package com.expensetracker.extraction

/**
 * Deterministic literal extraction from notification text — the exact-amount
 * regex helpers used by both the regex extractor and the dedup engine.
 * Zero arithmetic: values are copied verbatim, never computed.
 */
object LiteralExtraction {

    private val amountPattern = Regex(
        """(?:[₹$€£]|(?:INR|Rs\.?|USD|EUR|GBP)\s*)?(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    fun firstAmount(text: String): Double? {
        val match = amountPattern.find(text) ?: return null
        val raw = match.groupValues[1].replace(",", "")
        return raw.toDoubleOrNull()
    }

    fun merchantOrPayee(text: String): String? {
        val patterns = listOf(
            Regex("""\b(?:to|from|at|via|payee)\s+([A-Za-z0-9][A-Za-z0-9 .&'+-]{1,40}?)(?=\s+(?:via|UPI|Ref|on|from|to|at|PhonePe|Paytm|Google\s+Pay|Card|A\/c|a\/c|balance|bal|available)|[.,:](?:\s|$)|$)""", RegexOption.IGNORE_CASE),
            Regex("""UPI/([^/]{2,30})(?:/|$|\s)""", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            for (match in pattern.findAll(text)) {
                val raw = match.groupValues.getOrNull(1) ?: continue
                val cleaned = raw.trim().replace(Regex("""\s+"""), " ")
                if (cleaned.length >= 2 && !isGenericPayee(cleaned)) return cleaned
            }
        }
        return null
    }

    private fun isGenericPayee(s: String): Boolean {
        val low = s.lowercase().trim()
        return low == "your" || low == "you" || low.contains("account") || low.contains("a/c") ||
            low.startsWith("your ") || low.contains("balance") || low.contains(" bal") ||
            low.contains("available") || low.contains("bank")
    }

    fun accountReference(text: String): String? {
        val match = Regex("""(?:A/c\s*\*|Card\s*XX|card ending|account)\s*([A-Za-z0-9*]+)""", RegexOption.IGNORE_CASE)
            .find(text)
            ?: return null
        return match.value.trim()
    }
}
