package com.expensetracker.core.math

/**
 * Deterministic Math Engine (spec §3).
 *
 * 100% of the application's arithmetic lives here (and in SQLite aggregations).
 * No Small Language Model is ever asked to add, subtract, average, or derive
 * percentages. All inputs are raw transactions; all outputs are exact.
 */
object DeterministicMathEngine {

    /** Boundaries of the month containing [epochMillis], in the device's local timezone. */
    fun monthRange(epochMillis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Pair<Long, Long> {
        val zoned = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone)
        val month = zoned.toLocalDate().withDayOfMonth(1)
        val start = month.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        return start to end
    }

    /**
     * Month-to-date debit spend and the month-over-month delta.
     * Negative [deltaAmount] means spend decreased vs the previous month.
     */
    data class MonthSpendSummary(
        val currentMonthTotal: Double,
        val previousMonthTotal: Double,
        val deltaAmount: Double,
        val deltaPercent: Double,
        val transactionCount: Int,
    )

    fun monthSpendSummary(
        transactions: List<Pair<Double, Long>>, // (amount, timestamp) DEBIT rows
        nowEpochMillis: Long,
    ): MonthSpendSummary {
        val (curStart, _) = monthRange(nowEpochMillis)
        val (prevStart, prevEnd) = monthRange(curStart - 1L)

        var current = 0.0
        var previous = 0.0
        var count = 0
        for ((amount, timestamp) in transactions) {
            when {
                timestamp >= curStart -> {
                    current += amount
                    count++
                }
                timestamp >= prevStart && timestamp <= prevEnd -> previous += amount
            }
        }

        val deltaAmount = current - previous
        val deltaPercent = if (previous > 0.0) (deltaAmount / previous) * 100.0 else 0.0
        return MonthSpendSummary(
            currentMonthTotal = current,
            previousMonthTotal = previous,
            deltaAmount = deltaAmount,
            deltaPercent = deltaPercent,
            transactionCount = count,
        )
    }

    data class CategoryBreakdownRow(
        val categoryName: String,
        val totalAmount: Double,
        val transactionCount: Int,
    )

    fun categoryBreakdown(
        transactions: List<Triple<String, Double, Long>>, // (categoryName, amount, timestamp) DEBIT rows
        monthEpochMillis: Long,
    ): List<CategoryBreakdownRow> {
        val (start, end) = monthRange(monthEpochMillis)
        val totals = mutableMapOf<String, Double>()
        val counts = mutableMapOf<String, Int>()
        for ((categoryName, amount, timestamp) in transactions) {
            if (timestamp in start..end) {
                totals[categoryName] = (totals[categoryName] ?: 0.0) + amount
                counts[categoryName] = (counts[categoryName] ?: 0) + 1
            }
        }
        return totals.entries
            .map { (name, total) ->
                CategoryBreakdownRow(name, total, counts[name] ?: 0)
            }
            .sortedByDescending { it.totalAmount }
    }

    /** Sum of amounts; used for exact SQL-style fixtures. */
    fun sum(amounts: List<Double>): Double = amounts.fold(0.0) { acc, value -> acc + value }
}
