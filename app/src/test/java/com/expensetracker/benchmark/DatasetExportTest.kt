package com.expensetracker.benchmark

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import java.io.File

/**
 * Dev utility (not a real test — no assertions): exports BenchmarkCorpus as an
 * SFT (prompt, completion) JSONL dataset for fine-tuning the on-device
 * extraction model. Uses a minimal input (a short category list + the raw
 * notification), not the verbose production zero-shot prompt — the point of
 * fine-tuning is to bake the extraction rules into the model's weights via
 * training examples, not re-explain them every inference call. A short
 * prompt also removes the token-budget fragility that caused the on-device
 * MediaPipe model to fail in production.
 *
 * Run via: gradlew testDebugUnitTest --tests "*.DatasetExportTest"
 * Re-run whenever BenchmarkCorpus gains new samples, to keep the training
 * set in sync — this is now an iterate-and-retrain loop, not a one-off.
 */
class DatasetExportTest {

    @Serializable
    data class SftExample(val prompt: String, val completion: String)

    private val categoryList = "Food & Dining, Groceries, Transportation, Shopping, Bills & Utilities, " +
        "Rent, Entertainment, Health & Medical, Investment, Friend/Transfer, Salary/Income, Uncategorized"

    @Test
    fun exportSftDataset() {
        val outFile = File(
            """C:\Users\Sailesh\AppData\Local\Temp\claude\C--My-Agentic-Projects-expense-tracker\edfd3b39-b144-4d8f-a341-8075d4ed3cce\scratchpad\sft_dataset_minimal.jsonl"""
        )
        val json = Json { prettyPrint = false }
        outFile.bufferedWriter().use { writer ->
            for (sample in BenchmarkCorpus.samples) {
                val prompt = buildMinimalPrompt(sample.packageName, sample.title, sample.body)
                val completion = json.encodeToString(JsonObject.serializer(), buildCompletion(sample))
                writer.write(json.encodeToString(SftExample.serializer(), SftExample(prompt, completion)))
                writer.newLine()
            }
        }
        println("Exported ${BenchmarkCorpus.samples.size} samples to ${outFile.absolutePath}")
    }

    private fun buildMinimalPrompt(packageName: String, title: String, body: String): String {
        return "Categories: $categoryList\nNotification (pkg=$packageName): $title $body\nJSON:"
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

    private fun confidenceFor(merchant: String, category: String, txnType: String): Double = when {
        merchant.isNotBlank() && category == "Uncategorized" -> 0.45
        merchant.isNotBlank() && txnType != "UNKNOWN" -> 0.90
        merchant.isNotBlank() || txnType != "UNKNOWN" -> 0.75
        else -> 0.40
    }

    private fun buildCompletion(sample: BenchmarkCorpus.Sample): JsonObject {
        val isFinancial = sample.expectedType != "NON_FINANCIAL"
        val txnType = if (isFinancial) sample.expectedType else "DEBIT"
        val merchant = if (isFinancial) sample.expectedMerchant else ""
        val category = if (isFinancial) sample.expectedCategory else "Uncategorized"
        val amount = if (isFinancial) sample.expectedAmount else 0.0
        val confidence = if (isFinancial) confidenceFor(merchant, category, txnType) else 0.0

        return buildJsonObject {
            put("is_financial_transaction", isFinancial)
            put("amount", amount)
            put("currency", detectCurrency(sample.body))
            put("txn_type", txnType)
            put("merchant_or_payee", merchant)
            put("account_reference", JsonNull)
            put("category", category)
            put("confidence_score", confidence)
            put("reasoning", "")
        }
    }
}
