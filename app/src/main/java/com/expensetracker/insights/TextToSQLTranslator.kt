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

    private fun matchKnownPattern(question: String): QueryPlan? {
        // "How much did I spend on {merchant} this/last month?"
        val merchantMonth = Regex(
            """(?:how much|spend|spent).*(?:on|at)\s+(.+?)\s+(?:this|current)\s+month""",
            RegexOption.IGNORE_CASE,
        ).find(question)
        if (merchantMonth != null) {
            val merchant = merchantMonth.groupValues[1].trim()
            return QueryPlan.merchantMonth(merchant, currentMonth = true)
        }

        val merchantLastMonth = Regex(
            """(?:how much|spend|spent).*(?:on|at)\s+(.+?)\s+last\s+month""",
            RegexOption.IGNORE_CASE,
        ).find(question)
        if (merchantLastMonth != null) {
            val merchant = merchantLastMonth.groupValues[1].trim()
            return QueryPlan.merchantMonth(merchant, currentMonth = false)
        }

        // "How much did I spend this month?" / "total spending this month"
        if (question.contains(Regex("""(?:total|how much).*(?:spend|spent).*(?:this|current)\s+month""", RegexOption.IGNORE_CASE))) {
            return QueryPlan.totalMonth(currentMonth = true)
        }
        if (question.contains(Regex("""(?:total|how much).*(?:spend|spent).*last\s+month""", RegexOption.IGNORE_CASE))) {
            return QueryPlan.totalMonth(currentMonth = false)
        }

        // "How many transactions this month?"
        if (question.contains(Regex("""(?:how many|count).*(?:transaction|txn|order|payment).*(?:this|current)\s+month""", RegexOption.IGNORE_CASE))) {
            return QueryPlan.countMonth(currentMonth = true)
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
        private fun currentMonthRange(): Pair<Long, Long> {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.add(java.util.Calendar.MONTH, 1)
            val end = cal.timeInMillis - 1
            return start to end
        }

        private fun lastMonthRange(): Pair<Long, Long> {
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.MONTH, -1)
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.add(java.util.Calendar.MONTH, 1)
            val end = cal.timeInMillis - 1
            return start to end
        }

        fun merchantMonth(merchant: String, currentMonth: Boolean): QueryPlan {
            val (start, end) = if (currentMonth) currentMonthRange() else lastMonthRange()
            val label = if (currentMonth) "this month" else "last month"
            return QueryPlan(
                description = "Total spent on $merchant $label",
                execute = { dao ->
                    val value = dao.spendByMerchant(merchant, start, end)
                    "${Formatters.money(value)} spent on $merchant $label"
                },
            )
        }

        fun totalMonth(currentMonth: Boolean): QueryPlan {
            val (start, end) = if (currentMonth) currentMonthRange() else lastMonthRange()
            val label = if (currentMonth) "this month" else "last month"
            return QueryPlan(
                description = "Total spend $label",
                execute = { dao ->
                    val value = dao.totalDebitsBetween(start, end)
                    "${Formatters.money(value)} spent $label"
                },
            )
        }

        fun countMonth(currentMonth: Boolean): QueryPlan {
            val (start, end) = if (currentMonth) currentMonthRange() else lastMonthRange()
            val label = if (currentMonth) "this month" else "last month"
            return QueryPlan(
                description = "Transaction count $label",
                execute = { dao ->
                    val count = dao.debitCountBetween(start, end)
                    "$count transactions $label"
                },
            )
        }

        fun topCategory(): QueryPlan {
            val (start, end) = currentMonthRange()
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
            val (start, end) = currentMonthRange()
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
