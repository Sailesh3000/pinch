package com.expensetracker.ingestion

import java.util.Collections

/**
 * DeduplicationEngine — FR-DEDUP.
 *
 * Correlates dual-source notifications for a single economic transaction
 * (bank SMS + UPI app push). Rolling lookback window T = 180 seconds.
 * Match criteria (FR-DEDUP-02): |dt| <= 180s AND amount equal AND
 * (normalized payee equal OR currency equal).
 *
 * In-memory for Phase 1; survives within the process lifetime.
 */
class DeduplicationEngine(
    private val windowMillis: Long = DEDUP_WINDOW_MS,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    sealed interface Decision {
        data class Fresh(val dedupHash: String) : Decision
        data class Duplicate(val existingDedupHash: String) : Decision
    }

    private class Event(
        val dedupHash: String,
        val amount: Double,
        val currency: String,
        val payeeNorm: String?,
        val timestamp: Long,
    )

    private val events = Collections.synchronizedList(ArrayList<Event>())

    fun evaluate(
        amount: Double,
        currency: String,
        rawText: String,
        timestampEpoch: Long,
    ): Decision {
        val payeeNorm = normalizePayee(rawText)
        val dedupHash = DedupKey.compute(amount, currency, payeeNorm, timestampEpoch)
        val nowEpoch = now()

        synchronized(events) {
            events.removeIf { nowEpoch - it.timestamp > windowMillis }

            val duplicate = events.firstOrNull { candidate ->
                matches(candidate, amount, currency, payeeNorm, timestampEpoch)
            }
            if (duplicate != null) {
                return Decision.Duplicate(duplicate.dedupHash)
            }
            events.add(Event(dedupHash, amount, currency, payeeNorm, timestampEpoch))
            return Decision.Fresh(dedupHash)
        }
    }

    /**
     * FR-DEDUP-02: |dt| <= 180s AND amount_A == amount_B AND
     * (payee_norm_A == payee_norm_B OR currency_A == currency_B).
     */
    private fun matches(
        candidate: Event,
        amount: Double,
        currency: String,
        payeeNorm: String?,
        timestampEpoch: Long,
    ): Boolean {
        if (Math.abs(candidate.timestamp - timestampEpoch) > windowMillis) return false
        if (Math.abs(candidate.amount - amount) > 0.0001) return false
        val payeesMatch = candidate.payeeNorm != null && payeeNorm != null &&
            candidate.payeeNorm == payeeNorm
        val currenciesMatch = candidate.currency.equals(currency, ignoreCase = true)
        return payeesMatch || currenciesMatch
    }

    /**
     * Normalized payee from raw text: lower-cased, non-alphanumerics stripped,
     * matching "to X", "from X", "at X", "via X"-style targets.
     */
    fun normalizePayee(rawText: String): String? {
        val text = rawText.trim()
        val patterns = listOf(
            Regex("""\b(?:to|from|at)\s+([A-Za-z][A-Za-z0-9 .'-]{2,50})""", RegexOption.IGNORE_CASE),
            Regex("""\bvia\s+([A-Za-z][A-Za-z0-9 .'-]{2,30})\b""", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            val match = pattern.find(text)?.groupValues?.get(1) ?: continue
            val cleaned = match.trim().lowercase()
                .replace(Regex("[^a-z0-9]"), "")
            if (cleaned.length >= 3) return cleaned
        }
        return null
    }

    companion object {
        const val DEDUP_WINDOW_MS = 180_000L
    }
}
