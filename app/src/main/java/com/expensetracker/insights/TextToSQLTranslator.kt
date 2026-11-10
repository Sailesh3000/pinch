package com.expensetracker.insights

import android.util.Log
import com.expensetracker.core.common.Formatters
import com.expensetracker.core.database.dao.TransactionDao
import com.expensetracker.core.database.dao.CategorySpend
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * FR-INSIGHT-04: On-Device Natural Language Spend Q&A (Text-to-SQL).
 * Translates user questions into parameterized SQL queries.
 * The SLM does NOT perform math — SQLite handles all arithmetic.
 */
class TextToSQLTranslator(
    private val transactionDao: TransactionDao,
    private val categoryDao: com.expensetracker.core.database.dao.CategoryDao,
) {

    /**
     * Translates a natural language question to an executable query plan.
     * Returns null if the question cannot be translated.
     */
    suspend fun translateToSQL(question: String): QueryPlan? {
        val normalized = question.lowercase().trim()
        return matchKnownPattern(normalized)
    }

    /**
     * Executes a QueryPlan and returns the formatted result.
     */
    suspend fun executePlan(plan: QueryPlan): QueryResult {
        return try {
            val result = plan.execute(transactionDao)
            QueryResult(
                answer = result,
                rawValue = plan.numericValue,
                rowCount = 1,
                queryDescription = plan.description,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Query execution failed", e)
            QueryResult(
                answer = "Sorry, I couldn't process that query.",
                rawValue = 0.0,
                rowCount = 0,
                queryDescription = plan.description,
            )
        }
    }

    private fun matchKnownPattern(rawQuestion: String): QueryPlan? {
        val question = rawQuestion.trimEnd('?', '.', '!', ' ')
        // Without a period every question means this month, not "no answer".
        val period = QueryPeriod.entries.firstOrNull { question.contains(it.pattern) } ?: QueryPeriod.THIS_MONTH

        // "How much did I earn / receive this month?" - credits, not spend.
        if (question.contains(Regex("""\b(?:earn|earned|earning|earnings|receive|received|income|credited|got paid)\b"""))) {
            return QueryPlan.earned(period)
        }

        // "How much did I spend on {merchant|category} [period]?"
        val spendOn = Regex(
            """(?:how much|spend|spent|paid|pay)\b.*?\b(?:on|at|for)\s+(.+)$""",
        ).find(question)
        if (spendOn != null) {
            val target = spendOn.groupValues[1].replace(PERIOD_SUFFIX, "").trim()
            if (target.isNotEmpty() && target !in PERIOD_WORDS) {
                return QueryPlan.spendOn(target, period)
            }
        }

        // "How much did I spend this month?" / "total spending this week"
        if (question.contains(Regex("""(?:total|how much).*(?:spend|spent)"""))) {
            return QueryPlan.total(period)
        }

        // "How many transactions this month?"
        if (question.contains(Regex("""(?:how many|count).*(?:transaction|txn|order|payment)"""))) {
            return QueryPlan.count(period)
        }

        // "What category did I spend most on?"
        if (question.contains(Regex("""(?:which|what)\s+category.*(?:most|highest|biggest|top)""", RegexOption.IGNORE_CASE))) {
            return QueryPlan.topCategory()
        }

        // "What's my biggest expense?"
        if (question.contains(Regex("""(?:biggest|largest|highest).*(?:expense|spend|transaction|payment)""", RegexOption.IGNORE_CASE))) {
            return QueryPlan.largestTransaction()
        }

        return null
    }

    companion object {
        private const val TAG = "TextToSQLTranslator"

        /** Captures that are a period, not a merchant: "spend for the month". */
        private val PERIOD_WORDS = setOf("the month", "the week", "month", "week")

        /** Trailing period phrase to strip from a merchant capture: "food this week". */
        private val PERIOD_SUFFIX = Regex("""\s*\b(?:this|current|last|past)\s+(?:week|month)$|\s*\btoday$""")
    }
}

/** Time window a question asks about; ranges are in the device's local zone. */
enum class QueryPeriod(val label: String, val pattern: Regex) {
    TODAY("today", Regex("""\btoday\b""")),
    THIS_WEEK("this week", Regex("""\b(?:this|current)\s+week\b""")),
    LAST_WEEK("last week", Regex("""\b(?:last|past)\s+week\b""")),
    LAST_MONTH("last month", Regex("""\b(?:last|past)\s+month\b""")),
    THIS_MONTH("this month", Regex("""\b(?:this|current)\s+month\b"""));

    fun range(now: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): Pair<Long, Long> {
        val today = now.toLocalDate()
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val monthStart = today.withDayOfMonth(1)
        val (from, until) = when (this) {
            TODAY -> today to today.plusDays(1)
            THIS_WEEK -> weekStart to weekStart.plusWeeks(1)
            LAST_WEEK -> weekStart.minusWeeks(1) to weekStart
            THIS_MONTH -> monthStart to monthStart.plusMonths(1)
            LAST_MONTH -> monthStart.minusMonths(1) to monthStart
        }
        val zone = now.zone
        return from.atStartOfDay(zone).toInstant().toEpochMilli() to
            until.atStartOfDay(zone).toInstant().toEpochMilli() - 1L
    }
}

/**
 * A parameterized query plan with formatting.
 */
data class QueryPlan(
    val description: String,
    val numericValue: Double = 0.0,
    val execute: suspend (TransactionDao) -> String,
) {
    companion object {
        fun spendOn(target: String, period: QueryPeriod): QueryPlan {
            val (start, end) = period.range()
            val label = period.label
            return QueryPlan(
                description = "Total spent on $target $label",
                execute = { dao ->
                    // A category ("food" -> "Food & Dining") beats a merchant
                    // substring match, which would answer ₹0 for category questions.
                    val category = dao.categoryBreakdown(start, end)
                        .firstOrNull { categoryMatches(it.categoryName, target) }
                    if (category != null) {
                        "${Formatters.money(category.totalAmount)} spent on ${category.categoryName} $label"
                    } else {
                        val value = dao.spendByMerchant(target, start, end)
                        "${Formatters.money(value)} spent on $target $label"
                    }
                },
            )
        }

        /** Whole name, or any word of it ("food", "bills", "health") as a prefix. */
        internal fun categoryMatches(categoryName: String, target: String): Boolean {
            if (categoryName.equals(target, ignoreCase = true)) return true
            if (target.length < 3) return false
            return categoryName.split(Regex("""[^\p{L}]+"""))
                .any { word -> word.length >= 3 && word.startsWith(target, ignoreCase = true) }
        }

        fun earned(period: QueryPeriod): QueryPlan {
            val (start, end) = period.range()
            return QueryPlan(
                description = "Total received ${period.label}",
                execute = { dao ->
                    val value = dao.sumCreditsBetween(start, end)
                    "${Formatters.money(value)} received ${period.label}"
                },
            )
        }

        fun total(period: QueryPeriod): QueryPlan {
            val (start, end) = period.range()
            return QueryPlan(
                description = "Total spend ${period.label}",
                execute = { dao ->
                    val value = dao.totalDebitsBetween(start, end)
                    "${Formatters.money(value)} spent ${period.label}"
                },
            )
        }

        fun count(period: QueryPeriod): QueryPlan {
            val (start, end) = period.range()
            return QueryPlan(
                description = "Transaction count ${period.label}",
                execute = { dao ->
                    val count = dao.debitCountBetween(start, end)
                    "$count transactions ${period.label}"
                },
            )
        }

        fun topCategory(): QueryPlan {
            val (start, end) = QueryPeriod.THIS_MONTH.range()
            return QueryPlan(
                description = "Top category spend",
                execute = { dao ->
                    val breakdown = dao.categoryBreakdown(start, end)
                    val top = breakdown.maxByOrNull { it.totalAmount }
                    if (top == null) {
                        "No spending recorded this month yet"
                    } else {
                        "${top.categoryName} is your top category this month at ${Formatters.money(top.totalAmount)}"
                    }
                },
            )
        }

        fun largestTransaction(): QueryPlan {
            val (start, end) = QueryPeriod.THIS_MONTH.range()
            return QueryPlan(
                description = "Largest single transaction",
                execute = { dao ->
                    val value = dao.maxDebitBetween(start, end)
                    "${Formatters.money(value)} is your largest transaction this month"
                },
            )
        }
    }
}

data class QueryResult(
    val answer: String,
    val rawValue: Double,
    val rowCount: Int,
    val queryDescription: String,
)
