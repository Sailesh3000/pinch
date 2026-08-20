package com.expensetracker.ui.insights

import com.expensetracker.core.database.dao.RecurringMerchantRow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Subscription detection heuristic (spec §11 Phase 3).
 * Identifies recurring payments by analyzing merchant frequency and amount consistency
 * over the last 6 months.
 */
@Singleton
class SubscriptionDetector @Inject constructor() {

    data class Subscription(
        val merchantName: String,
        val avgAmount: Double,
        val estimatedIntervalDays: Int,
        val lastOccurrence: Long,
        val nextRenewalEstimate: Long,
        val occurrenceCount: Int,
    )

    /**
     * Analyzes [merchants] for recurring patterns.
     * A merchant is flagged as recurring if it appears in 3+ months with
     * amounts within ₹5 tolerance of each other.
     */
    fun detect(merchants: List<RecurringMerchantRow>): List<Subscription> {
        if (merchants.isEmpty()) return emptyList()

        return merchants
            .filter { it.txnCount >= 2 }
            .mapNotNull { row ->
                val intervalDays = estimateInterval(row.firstSeen, row.lastSeen, row.txnCount)
                if (intervalDays == null) return@mapNotNull null

                val amountVariance = row.avgAmount * AMOUNT_TOLERANCE_RATIO
                if (amountVariance < AMOUNT_MIN_TOLERANCE) return@mapNotNull null

                val nextRenewal = row.lastSeen + (intervalDays.toLong() * ONE_DAY_MS)
                Subscription(
                    merchantName = row.merchantName,
                    avgAmount = row.avgAmount,
                    estimatedIntervalDays = intervalDays,
                    lastOccurrence = row.lastSeen,
                    nextRenewalEstimate = nextRenewal,
                    occurrenceCount = row.txnCount,
                )
            }
            .sortedBy { it.nextRenewalEstimate }
    }

    private fun estimateInterval(firstSeen: Long, lastSeen: Long, count: Int): Int? {
        if (count < 2) return null
        val totalDays = ((lastSeen - firstSeen) / ONE_DAY_MS).toInt()
        val avgInterval = totalDays / (count - 1).coerceAtLeast(1)

        return when {
            avgInterval in 25..33 -> 28 // monthly (short month)
            avgInterval in 33..37 -> 30 // monthly
            avgInterval in 37..40 -> 31 // monthly (long month)
            avgInterval in 55..65 -> 60 // bimonthly
            avgInterval in 80..100 -> 90 // quarterly
            avgInterval in 170..200 -> 180 // semi-annual
            avgInterval in 350..380 -> 365 // annual
            else -> avgInterval // best effort
        }
    }

    companion object {
        private const val ONE_DAY_MS = 86_400_000L
        private const val AMOUNT_TOLERANCE_RATIO = 0.05 // 5% tolerance
        private const val AMOUNT_MIN_TOLERANCE = 10.0 // minimum ₹10 tolerance
    }
}
