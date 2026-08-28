package com.expensetracker.extraction

/**
 * Deterministic literal extraction from notification text — the exact-amount
 * regex helpers used by both the regex extractor and the dedup engine.
 * Zero arithmetic: values are copied verbatim, never computed.
 */
object LiteralExtraction {

    // Currency-anchored amounts are tried first regardless of position in the
    // string. A bare digit run with no currency prefix is only trusted as a
    // fallback, and only with a decimal point — real bank SMS almost always
    // prefixes the amount with INR/Rs/₹, so an un-prefixed bare INTEGER is far
    // more likely to be a masked account/reference number (e.g. "Acct XXX331")
    // than a real amount; requiring a decimal + a non-alphanumeric boundary
    // avoids grabbing digits embedded in such tokens.
    private val currencyAnchoredAmount = Regex(
        """(?:[₹$€£]|(?:INR|Rs\.?|USD|EUR|GBP))\s*(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val bareDecimalAmount = Regex(
        """(?<![A-Za-z0-9])(\d{1,3}(?:,\d{3})+\.\d{1,2}|\d+\.\d{1,2})"""
    )

    fun firstAmount(text: String): Double? {
        val match = currencyAnchoredAmount.find(text) ?: bareDecimalAmount.find(text) ?: return null
        val raw = match.groupValues[1].replace(",", "")
        return raw.toDoubleOrNull()
    }

    fun merchantOrPayee(text: String): String? {
        val patterns = listOf(
            Regex("""\b(?:to|from|at|via|payee)\s+([A-Za-z0-9][A-Za-z0-9 .&'+-]{1,40}?)(?=\s+(?:via|UPI|Ref|on|from|to|at|PhonePe|Paytm|Google\s+Pay|Card|A\/c|a\/c|balance|bal|available)|[.,:;](?:\s|$)|$)""", RegexOption.IGNORE_CASE),
            Regex("""UPI/([^/]{2,30})(?:/|$|\s)""", RegexOption.IGNORE_CASE),
            // P2P push notifications commonly put the counterparty's name as the
            // notification title, which NotificationProcessor prepends to the
            // body — e.g. "Varun sent ₹10 to you." Only fires when "sent" is
            // immediately after the name (not "...has been sent", which is the
            // impersonal "Money transfer successful" shape with no name present).
            Regex("""^([A-Za-z][A-Za-z .'-]{1,30}?)\s+(?:has\s+)?sent\b""", RegexOption.IGNORE_CASE),
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
