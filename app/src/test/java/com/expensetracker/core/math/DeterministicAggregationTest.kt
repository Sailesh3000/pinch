package com.expensetracker.core.math

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec §12.1 — DeterministicAggregationTest: verify Kotlin/SQLite math against
 * known fixture sets with 100% exact numerical match (zero-arithmetic rule).
 * Fixtures are built from real calendar boundaries of the current month.
 */
class DeterministicAggregationTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val now: Long = LocalDateTime.now()
        .withDayOfMonth(15)
        .withHour(12).withMinute(0).withSecond(0).withNano(0)
        .atZone(zone).toInstant().toEpochMilli()

    private fun epochAt(day: Int, hour: Int): Long =
        LocalDate.now().withDayOfMonth(day)
            .atStartOfDay(zone).toInstant().toEpochMilli() + hour * 3_600_000L

    @Test
    fun `month spend summary computes exact totals and delta`() {
        val (curStart, _) = DeterministicMathEngine.monthRange(now)
        val (prevStart, prevEnd) = DeterministicMathEngine.monthRange(curStart - 1L)
        val dayInCurrentMonth = (LocalDate.now().withDayOfMonth(1)).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayInPrevMonth = prevStart

        val transactions = listOf(
            850.0 to (dayInCurrentMonth + 5L * 86400000L), // current month
            1_240.25 to (dayInCurrentMonth + 10L * 86400000L), // current month
            5_000.0 to (dayInPrevMonth + 3L * 86400000L), // previous month
            100.0 to prevEnd, // previous month
        )
        val summary = DeterministicMathEngine.monthSpendSummary(transactions, now)

        assertEquals(850.0 + 1_240.25, summary.currentMonthTotal, 0.0001)
        assertEquals(5_000.0 + 100.0, summary.previousMonthTotal, 0.0001)
        assertEquals((850.0 + 1_240.25) - (5_000.0 + 100.0), summary.deltaAmount, 0.0001)
        assertEquals(2, summary.transactionCount)
    }

    @Test
    fun `delta percent is exact`() {
        val (curStart, _) = DeterministicMathEngine.monthRange(now)
        val (prevStart, _) = DeterministicMathEngine.monthRange(curStart - 1L)
        val transactions = listOf(
            1_420.0 to (curStart + 5L * 86400000L), // current
            1_075.0 to (prevStart + 5L * 86400000L), // previous
        )
        val summary = DeterministicMathEngine.monthSpendSummary(transactions, now)
        val expectedPercent = ((1_420.0 - 1_075.0) / 1_075.0) * 100.0
        assertEquals(expectedPercent, summary.deltaPercent, 0.0000001)
    }

    @Test
    fun `delta percent is zero when previous month has no spend`() {
        val (curStart, _) = DeterministicMathEngine.monthRange(now)
        val summary = DeterministicMathEngine.monthSpendSummary(
            listOf(500.0 to (curStart + 5L * 86400000L)),
            now,
        )
        assertEquals(500.0, summary.currentMonthTotal, 0.0001)
        assertEquals(0.0, summary.previousMonthTotal, 0.0001)
        assertEquals(0.0, summary.deltaPercent, 0.0001)
    }

    @Test
    fun `category breakdown groups and sorts by spend`() {
        val (curStart, _) = DeterministicMathEngine.monthRange(now)
        val (prevStart, _) = DeterministicMathEngine.monthRange(curStart - 1L)
        val rows = listOf(
            Triple("Food & Dining", 850.0, curStart + 3L * 86400000L),
            Triple("Food & Dining", 300.0, curStart + 5L * 86400000L),
            Triple("Transportation", 1_200.0, curStart + 7L * 86400000L),
            Triple("Food & Dining", 999.0, prevStart + 3L * 86400000L), // prev month, excluded
        )
        val breakdown = DeterministicMathEngine.categoryBreakdown(rows, now)

        assertEquals(2, breakdown.size)
        assertEquals("Transportation", breakdown[0].categoryName)
        assertEquals(1_200.0, breakdown[0].totalAmount, 0.0001)
        assertEquals("Food & Dining", breakdown[1].categoryName)
        assertEquals(850.0 + 300.0, breakdown[1].totalAmount, 0.0001)
        assertEquals(2, breakdown[1].transactionCount)
    }

    @Test
    fun `sum is exact`() {
        assertEquals(
            2_090.0,
            DeterministicMathEngine.sum(listOf(850.50, 1_240.25, -0.75)),
            0.0001,
        )
    }

    @Test
    fun `month range covers a full calendar month`() {
        val (start, end) = DeterministicMathEngine.monthRange(now)
        val localStart = LocalDate.now().withDayOfMonth(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(localStart, start)
        val nextMonth = LocalDate.now().withDayOfMonth(1).plusMonths(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(nextMonth - 1L, end)
        assertTrue(start <= now && now <= end)
    }

    @Test
    fun `monthSpendSummary handles empty input`() {
        val summary = DeterministicMathEngine.monthSpendSummary(emptyList(), now)
        assertEquals(0.0, summary.currentMonthTotal, 0.0001)
        assertEquals(0.0, summary.previousMonthTotal, 0.0001)
        assertEquals(0.0, summary.deltaPercent, 0.0001)
        assertEquals(0, summary.transactionCount)
    }
}
