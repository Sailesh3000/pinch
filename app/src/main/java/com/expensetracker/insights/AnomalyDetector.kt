package com.expensetracker.insights

import com.expensetracker.core.common.Formatters
import com.expensetracker.core.database.dao.TransactionDao
import java.util.Calendar

/**
 * FR-INSIGHT-04 anomaly detection: large spends and unfamiliar merchants.
 * Heuristic-only, no SLM involvement.
 */
class AnomalyDetector(
    private val transactionDao: TransactionDao,
) {

    /**
     * Detect anomalies in current month's transactions.
     * Returns list of anomalies sorted by severity (HIGH first).
     */
    suspend fun detect(): List<AnomalyItem> {
        val now = System.currentTimeMillis()
        val (monthStart, monthEnd) = monthRange(now)
        val transactions = transactionDao.debitsBetween(monthStart, monthEnd)

        if (transactions.isEmpty()) return emptyList()

        val anomalies = mutableListOf<AnomalyItem>()

        // 1. Large single transactions (> 2x the true daily average)
        val daysInMonth = ((monthEnd - monthStart) / DAY_MS).coerceAtLeast(1).toDouble()
        val dailyAvg = transactions.sumOf { it.amount } / daysInMonth
        val threshold = dailyAvg * 2.0
        for (txn in transactions) {
            if (txn.amount > threshold && txn.amount > LARGE_TXN_MIN) {
                anomalies.add(AnomalyItem(
                    merchantName = txn.merchantName,
                    description = "${Formatters.money(txn.amount)} spent at ${txn.merchantName} " +
                        "(2x your daily average of ${Formatters.money(dailyAvg)})",
                    shortDescription = "Unusually large: ${Formatters.money(txn.amount)} at ${txn.merchantName}",
                    severity = if (txn.amount > threshold * 2) AnomalySeverity.HIGH else AnomalySeverity.MEDIUM,
                    timestamp = txn.timestamp,
                    amount = txn.amount,
                ))
            }
        }

        // 2. Unfamiliar merchants (only appeared once in last 90 days)
        val ninetyDaysAgo = now - (90L * DAY_MS)
        val allMerchants = transactionDao.merchantFrequency(ninetyDaysAgo, now)
        val familiarMerchants = allMerchants.filter { it.txnCount >= 2 }.map { it.merchantName }.toSet()

        for (txn in transactions) {
            if (txn.merchantName !in familiarMerchants && txn.amount > UNFAMILIAR_MIN) {
                // Don't duplicate if already flagged as large
                if (anomalies.none { it.merchantName == txn.merchantName && it.amount == txn.amount }) {
                    anomalies.add(AnomalyItem(
                        merchantName = txn.merchantName,
                        description = "First-time merchant: ${txn.merchantName} " +
                            "(${Formatters.money(txn.amount)})",
                        shortDescription = "New merchant: ${txn.merchantName}",
                        severity = AnomalySeverity.LOW,
                        timestamp = txn.timestamp,
                        amount = txn.amount,
                    ))
                }
            }
        }

        return anomalies.sortedByDescending { it.severity }
    }

    private fun monthRange(now: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis - 1
        return start to end
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val LARGE_TXN_MIN = 500.0 // ₹500 minimum for "large" anomaly
        private const val UNFAMILIAR_MIN = 100.0 // ₹100 minimum for "unfamiliar" anomaly
    }
}
