package com.expensetracker.extraction

/**
 * Tier 1 fallback / deterministic extractor. Copies literal amount and merchant
 * tokens via regex — zero arithmetic, zero network. Used when Gemini Nano is
 * not available on the device so the pipeline remains fully functional.
 */
open class DeterministicRegexExtractor : TransactionExtractor {

    override val engineName: String = "Deterministic Regex Engine"

    open override suspend fun isAvailable(): Boolean = true

    // A bare currency symbol/code is deliberately NOT sufficient on its own —
    // see PreFilterEngine.positivePattern for why. Mirror its full anchor set:
    // a transaction verb, or an account/UPI reference (real bank SMS often
    // omits a verb, e.g. "Rs 1,234.50 from A/c *9876, Ref ...").
    private val financialSignal = Regex(
        """\b(?:paid|debited|credited|spent|received|refund|purchase|payment|sent|transfer|withdrawn?|deposited|emi)\b|\b(?:dr|cr)\.?\s+(?:inr|rs\.?|usd|eur|gbp|[₹$€£])|\bupi\s*ref\b|\bupi\s*[:-]?\s*\d{6,}|\ba/c\s*\*\d|\bacct\.?\s*[a-z*]{0,5}\d|\bvpa\b""",
        RegexOption.IGNORE_CASE
    )

    private val promotionalSignal = Regex(
        """\b(cashback|reward(?:\s*points?)?|bonus(?:\s*points?)?|wallet\s*offer|gift\s*card|voucher|won|congratulations|instant\s*discount|special\s*offer|limited\s*period|sale\s*is\s*live|extra\s*off)\b|flat\s*\d+%|\d+%\s*off|up\s*to\s*\d+%""",
        RegexOption.IGNORE_CASE
    )

    open override suspend fun extract(
        packageName: String,
        title: String,
        body: String,
        timestampEpoch: Long,
    ): ExtractionResult? {
        val text = "$title $body".trim()
        if (promotionalSignal.containsMatchIn(text)) return null
        if (!financialSignal.containsMatchIn(text)) return null
        val amount = LiteralExtraction.firstAmount(text) ?: return null
        val txnType = detectType(text)
        val merchant = LiteralExtraction.merchantOrPayee(text)
        val category = merchant?.let(::inferCategory) ?: "Uncategorized"
        // Generic personal transfers route to the clarification queue: merchant
        // present but no category signal → deliberately low confidence (< 0.70).
        val confidence = when {
            merchant != null && category == "Uncategorized" -> 0.45f
            merchant != null && txnType != "UNKNOWN" -> 0.90f
            merchant != null || txnType != "UNKNOWN" -> 0.75f
            else -> 0.40f
        }
        return ExtractionResult(
            isFinancialTransaction = true,
            amount = amount,
            currency = detectCurrency(text),
            txnType = txnType,
            merchantOrPayee = merchant ?: "",
            accountReference = LiteralExtraction.accountReference(text),
            category = category,
            confidenceScore = confidence,
            reasoning = "Deterministic regex extraction; merchant=$merchant, type=$txnType",
        )
    }

    private fun detectType(text: String): String {
        // "<Person> sent ₹X to you[r account]" describes the OTHER party's
        // action — the amount is incoming, so it's a CREDIT even though "sent"
        // (a debit word) appears. Check this direction override first.
        val sentToYou = Regex("""\bsent\b.{0,40}?\bto\s+your?\b""", RegexOption.IGNORE_CASE)
        if (sentToYou.containsMatchIn(text)) return "CREDIT"

        val debit = Regex(
            """\b(debited|spent|paid|sent|purchase|payment|withdraw|transfer)\b|\bdr\.?\s+(?:inr|rs\.?|usd|eur|gbp|[₹$€£])""",
            RegexOption.IGNORE_CASE
        )
        val credit = Regex(
            """\b(credited|received|refund|deposited)\b|\bcr\.?\s+(?:inr|rs\.?|usd|eur|gbp|[₹$€£])""",
            RegexOption.IGNORE_CASE
        )
        return when {
            debit.containsMatchIn(text) && !credit.containsMatchIn(text) -> "DEBIT"
            credit.containsMatchIn(text) -> "CREDIT"
            else -> "UNKNOWN"
        }
    }

    private fun detectCurrency(text: String): String {
        val normalized = text.uppercase()
        return when {
            Regex("""\bUSD\b|\$""").containsMatchIn(normalized) -> "USD"
            Regex("""\bEUR\b|€""").containsMatchIn(normalized) -> "EUR"
            Regex("""\bGBP\b|£""").containsMatchIn(normalized) -> "GBP"
            else -> "INR"
        }
    }

    fun inferCategory(merchant: String): String {
        val m = merchant.lowercase()
        return when {
            m.contains("swiggy") || m.contains("zomato") || m.contains("uber eats") ||
                m.contains("dominos") || m.contains("pizza") || m.contains("kfc") ||
                m.contains("mcdonald") || m.contains("starbucks") -> "Food & Dining"
            m.contains("bigbasket") || m.contains("dmart") || m.contains("blinkit") ||
                m.contains("instamart") || m.contains("zepto") || m.contains("grocery") ||
                m.contains("reliance fresh") || m.contains("indane") || m.contains("gas bill") -> "Groceries"
            m.contains("uber") || m.contains("ola") || m.contains("rapido") ||
                m.contains("redbus") || m.contains("irctc") || m.contains("makemytrip") ||
                m.contains("metro") || m.contains("fuel") || m.contains("indian oil") ||
                m.contains("shell") || m.contains("petrol") || m.contains("goibibo") ||
                m.contains("air india") || m.contains("airasia") || m.contains("vistara") ||
                m.contains("lufthansa") -> "Transportation"
            m.contains("amazon") || m.contains("flipkart") || m.contains("myntra") ||
                m.contains("ajio") || m.contains("nike") || m.contains("adidas") ||
                m.contains("meesho") || m.contains("snapdeal") || m.contains("lenskart") ||
                m.contains("h&m") || m.contains("forever21") || m.contains("samsung") ||
                m.contains("apple") -> "Shopping"
            m.contains("netflix") || m.contains("spotify") || m.contains("prime") ||
                m.contains("hotstar") || m.contains("youtube") || m.contains("play store") ||
                m.contains("bookmyshow") || m.contains("pvr") || m.contains("disney") ||
                m.contains("marriott") || m.contains("taj hotels") -> "Entertainment"
            m.contains("jio") || m.contains("airtel") || m.contains("vodafone") ||
                m.contains("electricity") || m.contains("bills") || m.contains("broadband") ||
                m.contains("insurance premium") || m.contains("gas") ||
                m.contains("recharge") -> "Bills & Utilities"
            m.contains("rent") || m.contains("landlord") -> "Rent"
            m.contains("hospital") || m.contains("pharmacy") || m.contains("apollo") ||
                m.contains("fortis") || m.contains("1mg") || m.contains("doctor") ||
                m.contains("clinic") || m.contains("med") -> "Health & Medical"
            m.contains("zerodha") || m.contains("groww") || m.contains("lic") ||
                m.contains("mutual") || m.contains("stock") || m.contains("investment") ||
                m.contains("sip") || m.contains("adobe") -> "Investment"
            else -> "Uncategorized"
        }
    }
}
