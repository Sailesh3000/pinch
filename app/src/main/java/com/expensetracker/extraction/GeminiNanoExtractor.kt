package com.expensetracker.extraction

import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * Tier 1 primary extractor — Gemini Nano via the ML Kit GenAI Prompt API
 * (FR-EXTRACT-02). Uses the literal-extractor system prompt from spec §6.2 and
 * enforces zero arithmetic: the model only copies text tokens.
 *
 * The engine is guarded by runtime `checkStatus()` and is only invoked when
 * AICore reports Gemini Nano AVAILABLE. On unsupported hardware this extractor
 * returns null and the pipeline falls back to the deterministic extractor.
 */
open class GeminiNanoExtractor(
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TransactionExtractor {

    override val engineName: String = "Gemini Nano (ML Kit GenAI Prompt API)"

    private val generativeModel by lazy {
        com.google.mlkit.genai.prompt.Generation.getClient()
    }

    open override suspend fun isAvailable(): Boolean {
        return try {
            withTimeoutOrNull(3_000L) {
                generativeModel.checkStatus() == com.google.mlkit.genai.common.FeatureStatus.AVAILABLE
            } ?: false
        } catch (t: Throwable) {
            Log.d(TAG, "Gemini Nano availability check failed: ${t.message}")
            false
        }
    }

    open override suspend fun extract(
        packageName: String,
        title: String,
        body: String,
        timestampEpoch: Long,
    ): ExtractionResult? {
        if (!isAvailable()) return null
        return try {
            val response = generativeModel.generateContent(
                com.google.mlkit.genai.prompt.generateContentRequest(
                    com.google.mlkit.genai.prompt.TextPart(buildPrompt(packageName, title, body))
                ) {
                    temperature = 0.0f
                    maxOutputTokens = 600
                }
            )
            val text = response.candidates.firstOrNull()?.text?.trim()
            if (text.isNullOrBlank()) return null
            parseStrictJson(text)
        } catch (_: Throwable) {
            null
        }
    }

    open override suspend fun generateText(prompt: String): String? {
        if (!isAvailable()) return null
        return try {
            val response = generativeModel.generateContent(
                com.google.mlkit.genai.prompt.generateContentRequest(
                    com.google.mlkit.genai.prompt.TextPart(prompt)
                ) {
                    temperature = 0.0f
                    maxOutputTokens = 400
                }
            )
            response.candidates.firstOrNull()?.text?.trim()
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseStrictJson(text: String): ExtractionResult? {
        val jsonText = extractJsonBlock(text) ?: text
        val result = json.decodeFromString<ExtractionResult>(jsonText)
        return result.takeIf { it.isFinancialTransaction }
    }

    private fun extractJsonBlock(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun buildPrompt(packageName: String, title: String, body: String): String {
        val system = """
            You are an on-device financial transaction parser. Your task is to extract structured transaction data from raw Android notification text emitted by banking apps, UPI payment systems (Google Pay, PhonePe, Paytm, CRED), credit card alerts, and bank SMS messages.

            CRITICAL EXTRACTION RULES:
            1. Extract the exact numerical amount literal present in the text. DO NOT perform any mathematical calculations, splits, or currency conversions.
            2. Clean merchant/payee names (e.g. "UPI/SWIGGY/PAYTM/1234" -> "Swiggy").
            3. Determine if it is a DEBIT (money spent/withdrawn) or CREDIT (money received/refund).
            4. Assign the most accurate category from the allowed enum: "Food & Dining", "Groceries", "Transportation", "Shopping", "Bills & Utilities", "Rent", "Entertainment", "Health & Medical", "Investment", "Friend/Transfer", "Salary/Income", "Uncategorized".
            5. Provide a realistic confidence_score between 0.00 and 1.00:
               - 0.90+: Clear merchant with unambiguous category (e.g. "Swiggy", "Uber", "Netflix").
               - 0.70-0.89: Clear debit/credit, known merchant, but broad category.
               - Below 0.70: Generic personal transfer (e.g. "Paid Rs 500 to Ramesh Kumar"), ambiguous format, or unclear purpose.
            6. Set "is_financial_transaction" to false for cashback credits, reward/loyalty points, promotional offers, discount/sale announcements, and marketing messages — these are NOT real bank debits/credits even if they mention an amount or use the word "credited".
            7. Output strict JSON matching this schema exactly. No markdown outside JSON.

            EXAMPLES OF NON-FINANCIAL (is_financial_transaction=false):
            - "You've earned ₹50 cashback on your Swiggy order" -> promotional cashback, not a real transaction.
            - "Congratulations! You won a reward of Rs 100. Claim now" -> promotional reward, not a real transaction.
            - "Flat 20% off + extra 10% instant cashback on your next order" -> sale/discount announcement, not a real transaction.
        """.trimIndent()

        return """
            $system

            Notification source package: $packageName
            Title: $title
            Body: $body

            Respond with JSON only, in this shape:
            {
              "is_financial_transaction": true,
              "amount": 0.0,
              "currency": "INR",
              "txn_type": "DEBIT",
              "merchant_or_payee": "",
              "account_reference": null,
              "category": "Uncategorized",
              "confidence_score": 0.0,
              "reasoning": ""
            }
        """.trimIndent()
    }

    companion object {
        private const val TAG = "GeminiNanoExtractor"
    }
}
