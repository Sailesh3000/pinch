package com.expensetracker.ingestion

/**
 * Zero-computation in-memory regex pre-filter (FR-INGEST-03).
 * - Positive triggers indicate a candidate financial notification.
 * - Negative blockers drop the notification immediately (OTP, promos, security alerts).
 */
class PreFilterEngine(private val whitelist: PackageWhitelist) {

    private val positivePattern = Regex(
        """
        (?ix)
        \b(debited|credited|spent|transferred|paid|sent\s+to|received\s+from|purchase|refund)\b
        |\bupi\s*ref\b
        |\ba/c\s*\*\d
        |\bvpa\b
        |\b(?:inr|rs\.?|usd|eur|gbp)\b
        |[₹$€£]
        """.trimIndent()
    )

    private val negativePattern = Regex(
        """
        (?ix)
        \b(otp|verification\s+code|security\s+code|login\s+alert|pre[- ]approved\s+loan|exclusive\s+offer|discount\s+coupon|promo\s+code)\b
        """.trimIndent()
    )

    fun isFinancialCandidate(packageName: String, title: String, body: String): Boolean {
        if (!whitelist.isWhitelisted(packageName)) return false
        val text = "$title $body"
        if (negativePattern.containsMatchIn(text)) return false
        return positivePattern.containsMatchIn(text)
    }
}
