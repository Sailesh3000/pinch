package com.expensetracker.extraction

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import kotlinx.serialization.json.Json
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Tier 1 fallback extractor — MediaPipe LLM Inference with Gemma 3 1B
 * (FR-EXTRACT-02, spec §11). Used on devices where Gemini Nano (AICore) is
 * unavailable. Uses the same literal-extractor system prompt as GeminiNanoExtractor.
 *
 * Model must be downloaded to internal storage first (see ModelDownloader).
 * Uses synchronous generateResponse() to avoid session threading issues.
 */
open class MediaPipeExtractor(
    private val context: Context?,
    private val modelPath: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TransactionExtractor {

    private val regexExtractor = DeterministicRegexExtractor()

    override val engineName: String = "MediaPipe Qwen2.5-0.5B (Local Engine)"

    private var llmInference: LlmInference? = null
    private val initLock = Any()

    private fun ensureInitialized(): Boolean {
        if (llmInference != null) return true
        val ctx = context ?: return false
        synchronized(initLock) {
            if (llmInference != null) return true
            return try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(MAX_TOKENS)
                    .build()
                llmInference = LlmInference.createFromOptions(ctx.applicationContext, options)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize MediaPipe LLM", e)
                false
            }
        }
    }

    open override suspend fun isAvailable(): Boolean = ensureInitialized()

    open override suspend fun extract(
        packageName: String,
        title: String,
        body: String,
        timestampEpoch: Long,
    ): ExtractionResult? {
        if (!ensureInitialized()) return null
        val inference = llmInference ?: return null

        return try {
            val sessionOptions = LlmInferenceSessionOptions.builder()
                .setTemperature(0.0f)
                .setTopK(1)
                .build()

            val session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
            val prompt = buildPrompt(packageName, title, body)

            val responseText = generateSync(session, prompt)

            session.close()

            if (responseText.isNullOrBlank()) {
                Log.w(TAG, "MediaPipe returned blank response")
                return null
            }
            Log.d(TAG, "MediaPipe raw response: $responseText")
            val parsed = parseStrictJson(responseText) ?: return null
            val merchant = cleanMerchant(parsed.merchantOrPayee, title, body)
            val category = if (parsed.category.isBlank() || parsed.category.equals("Uncategorized", true)) {
                regexExtractor.inferCategory(merchant)
            } else {
                parsed.category
            }
            return parsed.copy(merchantOrPayee = merchant, category = category)
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe extraction failed", e)
            null
        }
    }

    /**
     * Free-form narrative completion (FR-INSIGHT-02). Reuses the same session
     * machinery as extraction but returns the raw text instead of parsed JSON.
     */
    open override suspend fun generateText(prompt: String): String? {
        if (!ensureInitialized()) return null
        val inference = llmInference ?: return null
        return try {
            val sessionOptions = LlmInferenceSessionOptions.builder()
                .setTemperature(0.0f)
                .setTopK(1)
                .build()
            val session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
            val responseText = generateSync(session, prompt)
            session.close()
            responseText?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe narrative generation failed", e)
            null
        }
    }

    /**
     * Synchronous response generation using a CountDownLatch.
     * The MediaPipe callback streams partial-result deltas; we accumulate them
     * and complete once the `done` flag is set.
     */
    private fun generateSync(session: LlmInferenceSession, prompt: String): String? {
        val result = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)

        session.addQueryChunk(prompt)
        val accumulated = StringBuilder()
        session.generateResponseAsync { partialResult, done ->
            if (partialResult != null && partialResult.isNotEmpty()) {
                accumulated.append(partialResult)
            }
            if (done) {
                result.set(accumulated.toString())
                latch.countDown()
            }
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return result.get()
    }

    /**
     * Post-processes the model's merchant output. The 0.5B model often returns
     * the raw UPI string (e.g. "UPI/SWIGGY/PHONEPE/1234"); fall back to the
     * deterministic cleaner for a canonical merchant name.
     */
    private fun cleanMerchant(modelMerchant: String, title: String, body: String): String {
        val raw = modelMerchant.trim()
        val looksRaw = raw.uppercase().contains("UPI") ||
            raw.contains("/") ||
            raw.any { it.isDigit() } ||
            raw.contains("PAYTM") ||
            raw.contains("PHONEPE")
        if (!looksRaw) return raw
        return LiteralExtraction.merchantOrPayee("$title $body")
            ?: raw
    }

    private fun parseStrictJson(text: String): ExtractionResult? {
        val jsonText = extractJsonBlock(text) ?: text
        return try {
            val result = json.decodeFromString<ExtractionResult>(jsonText)
            result.takeIf { it.isFinancialTransaction }
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse failed: ${e.message}")
            null
        }
    }

    private fun extractJsonBlock(text: String): String? {
        // Strip any markdown code fences the model may wrap around JSON.
        val cleaned = text
            .replace(Regex("""```[a-zA-Z]*"""), "")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return cleaned.substring(start, end + 1)
    }

    private fun buildPrompt(packageName: String, title: String, body: String): String {
        val system = """
            You are an on-device financial transaction parser. Your task is to extract structured transaction data from raw Android notification text emitted by banking apps, UPI payment systems (Google Pay, PhonePe, Paytm, CRED), credit card alerts, and bank SMS messages.

            CRITICAL EXTRACTION RULES:
            1. Extract the exact numerical amount literal present in the text. DO NOT perform any mathematical calculations, splits, or currency conversions.
            2. Clean merchant names (e.g. "UPI/ACMEMART/PAYTM/1234" -> "ACMEMART"). If a person's name is given (e.g. as the notification title, like "Varun sent ₹10 to you"), use that name as merchant_or_payee with category "Friend/Transfer". For a bare UPI handle/email with no name anywhere (e.g. "john@okhdfc") leave merchant_or_payee EMPTY — never invent one.
            3. DEBIT = money spent/withdrawn. CREDIT = money received/refund. "<Person> sent ₹X to you/your account" means YOU received it — CREDIT, despite the word "sent". Banks abbreviate this "Dr."/"Cr." (e.g. "Acct XXX331 Dr. INR 10.00").
            4. Category enum: "Food & Dining", "Groceries", "Transportation", "Shopping", "Bills & Utilities", "Rent", "Entertainment", "Health & Medical", "Investment", "Friend/Transfer", "Salary/Income", "Uncategorized".
            5. confidence_score: 0.90+ = clear named business merchant. 0.70-0.89 = known merchant, broad category. Below 0.70 = generic transfer to a UPI handle/person, ambiguous.
            6. is_financial_transaction=false for cashback/reward/points credits and "X% off"/sale/promo messages — these aren't real transactions even if an amount or "credited" appears.
            7. Output strict JSON matching the schema exactly. No markdown outside JSON.

            EXAMPLE: "₹250 sent to john@okhdfc" -> {"is_financial_transaction": true, "amount": 250.0, "currency": "INR", "txn_type": "DEBIT", "merchant_or_payee": "", "account_reference": null, "category": "Uncategorized", "confidence_score": 0.4, "reasoning": ""}
        """.trimIndent()

        return """
            $system

            Notification source package: $packageName
            Title: $title
            Body: $body

            Extract the notification above (not the example). JSON only, in this shape:
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

    fun close() {
        llmInference?.close()
        llmInference = null
    }

    companion object {
        private const val TAG = "MediaPipeExtractor"
        private const val MAX_TOKENS = 1024
        private const val TIMEOUT_SECONDS = 30L
    }
}
