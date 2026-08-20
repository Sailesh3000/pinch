package com.expensetracker.insights

import android.util.Log
import com.expensetracker.extraction.ExtractorChain
import com.expensetracker.extraction.TransactionExtractor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * FR-INSIGHT-02: Guardrailed SLM Spending Narrator.
 * Pre-computed fact tokens are injected into the prompt; the SLM only composes
 * natural language around them — zero arithmetic in the prompt.
 */
class NarrativeGenerator(
    private val extractorChain: ExtractorChain,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /**
     * Generates a 2-sentence narrative from pre-computed facts.
     * The SLM composes natural language; math is already done by SQLite.
     */
    suspend fun generateSpendingSummary(facts: SpendingFacts): NarrativeResult {
        val prompt = buildSummaryPrompt(facts)
        val raw = callSlm(prompt) ?: return NarrativeResult(
            narrative = fallbackSummary(facts),
            claims = emptyList(),
        )
        return parseNarrative(raw, facts)
    }

    /**
     * Generates an anomaly narrative for unusual spending patterns.
     */
    suspend fun generateAnomalyNarrative(anomalies: List<AnomalyItem>): NarrativeResult {
        if (anomalies.isEmpty()) return NarrativeResult(
            narrative = "No unusual spending patterns detected this month.",
            claims = emptyList(),
        )
        val prompt = buildAnomalyPrompt(anomalies)
        val raw = callSlm(prompt) ?: return NarrativeResult(
            narrative = fallbackAnomalySummary(anomalies),
            claims = anomalies.map { NarrativeClaim(it.merchantName, DeepLinkType.MERCHANT, it.merchantName) },
        )
        return parseAnomalyNarrative(raw, anomalies)
    }

    private suspend fun callSlm(prompt: String): String? {
        return try {
            // Route the guardrailed narrative prompt to the best available LLM
            // (Gemini Nano → MediaPipe Qwen). Deterministic regex has no model,
            // so this returns null and we fall back to the deterministic summary.
            extractorChain.generateText(prompt)
        } catch (e: Exception) {
            Log.w(TAG, "SLM narrative generation failed", e)
            null
        }
    }

    internal fun buildSummaryPrompt(facts: SpendingFacts): String {
        return """
            You are a friendly financial assistant. Using ONLY the facts below, write a 2-sentence
            conversational summary of this month's spending. Do NOT perform any calculations.
            Use the exact numbers provided. Be concise and natural.

            FACTS:
            - Total spent this month: ${facts.currentSpend}
            - vs last month: ${facts.deltaDescription}
            - Number of transactions: ${facts.transactionCount}
            - Top category: ${facts.topCategoryName} (${facts.topCategoryAmount})
            - Top merchant: ${facts.topMerchantName} (${facts.topMerchantAmount}, ${facts.topMerchantCount} times)
            - Daily average: ${facts.dailyAverage}
        """.trimIndent()
    }

    internal fun buildAnomalyPrompt(anomalies: List<AnomalyItem>): String {
        val items = anomalies.joinToString("\n") { "- ${it.description}" }
        return """
            You are a friendly financial assistant. Using ONLY the facts below, write a 1-2 sentence
            alert about unusual spending patterns. Be helpful, not alarming.

            ANOMALIES:
            $items
        """.trimIndent()
    }

    private fun parseNarrative(raw: String, facts: SpendingFacts): NarrativeResult {
        // Extract claims that can be deep-linked
        val claims = mutableListOf<NarrativeClaim>()

        // Find merchant mentions
        if (raw.contains(facts.topMerchantName, ignoreCase = true)) {
            claims.add(NarrativeClaim(
                text = facts.topMerchantName,
                deepLinkType = DeepLinkType.MERCHANT,
                filterValue = facts.topMerchantName,
            ))
        }

        // Find category mentions
        if (raw.contains(facts.topCategoryName, ignoreCase = true)) {
            claims.add(NarrativeClaim(
                text = facts.topCategoryName,
                deepLinkType = DeepLinkType.CATEGORY,
                filterValue = facts.topCategoryName,
            ))
        }

        return NarrativeResult(
            narrative = raw.trim().take(280),
            claims = claims,
        )
    }

    private fun parseAnomalyNarrative(raw: String, anomalies: List<AnomalyItem>): NarrativeResult {
        val claims = anomalies.map { item ->
            NarrativeClaim(
                text = item.merchantName,
                deepLinkType = DeepLinkType.MERCHANT,
                filterValue = item.merchantName,
            )
        }
        return NarrativeResult(
            narrative = raw.trim().take(280),
            claims = claims,
        )
    }

    private fun fallbackSummary(facts: SpendingFacts): String {
        return buildString {
            append("You spent ${facts.currentSpend} this month across ${facts.transactionCount} transactions")
            if (facts.deltaDescription.isNotEmpty()) {
                append(", ${facts.deltaDescription} vs last month")
            }
            append(". ${facts.topMerchantName} was your biggest expense at ${facts.topMerchantAmount}.")
        }
    }

    private fun fallbackAnomalySummary(anomalies: List<AnomalyItem>): String {
        return when (anomalies.size) {
            1 -> "Heads up: ${anomalies.first().description}"
            else -> "Heads up: ${anomalies.size} unusual spending patterns detected. " +
                anomalies.joinToString("; ") { it.shortDescription }
        }
    }

    companion object {
        private const val TAG = "NarrativeGenerator"
    }
}

@Serializable
data class SpendingFacts(
    val currentSpend: String,
    val previousSpend: String,
    val deltaDescription: String,
    val transactionCount: Int,
    val topCategoryName: String,
    val topCategoryAmount: String,
    val topMerchantName: String,
    val topMerchantAmount: String,
    val topMerchantCount: Int,
    val dailyAverage: String,
)

data class NarrativeResult(
    val narrative: String,
    val claims: List<NarrativeClaim>,
)

data class NarrativeClaim(
    val text: String,
    val deepLinkType: DeepLinkType,
    val filterValue: String,
)

enum class DeepLinkType {
    MERCHANT,
    CATEGORY,
    DATE_RANGE,
}

data class AnomalyItem(
    val merchantName: String,
    val description: String,
    val shortDescription: String,
    val severity: AnomalySeverity,
    val timestamp: Long,
    val amount: Double,
)

enum class AnomalySeverity { LOW, MEDIUM, HIGH }
