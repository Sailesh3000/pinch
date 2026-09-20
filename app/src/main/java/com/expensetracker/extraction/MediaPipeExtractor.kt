package com.expensetracker.extraction

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Tier 1 fallback extractor — on-device LLM inference with a fine-tuned
 * Qwen2.5-0.5B (FR-EXTRACT-02, spec §11). Used on devices where Gemini Nano
 * (AICore) is unavailable. Runs on the LiteRT-LM runtime (`Engine`/
 * `Conversation`), not the older MediaPipe `LlmInference` API — the
 * `.litertlm` file this app ships embeds a HuggingFace-format tokenizer,
 * which only the LiteRT-LM runtime knows how to load; `LlmInference`
 * requires a SentencePiece-format tokenizer and fails with
 * "SentencePiece tokenizer is not found in the model" on this file.
 *
 * Model must be resolved to a real file path first (see ModelAssetProvider /
 * ModelDownloader). A fresh, history-free `Conversation` is created per call
 * so extraction stays stateless and deterministic across notifications.
 */
open class MediaPipeExtractor(
    private val context: Context?,
    private val modelPath: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TransactionExtractor {

    private val regexExtractor = DeterministicRegexExtractor()

    override val engineName: String = "MediaPipe Qwen2.5-0.5B (Local Engine)"

    private var engine: Engine? = null
    private val initLock = Any()

    /** Deterministic sampling — greedy decoding, matches the old temperature=0/topK=1 setup. */
    private val samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0)

    private suspend fun ensureInitialized(): Boolean {
        if (engine != null) return true
        val ctx = context ?: return false
        return withContext(Dispatchers.IO) {
            synchronized(initLock) {
                if (engine != null) return@withContext true
            }
            val newEngine = try {
                Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        cacheDir = ctx.applicationContext.cacheDir.path,
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to construct LiteRT-LM engine", e)
                return@withContext false
            }
            try {
                newEngine.initialize()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize LiteRT-LM engine", e)
                return@withContext false
            }
            synchronized(initLock) { engine = newEngine }
            true
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
        val prompt = buildPrompt(packageName, title, body)
        val responseText = generateSync(prompt) ?: return null

        if (responseText.isBlank()) {
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
    }

    /**
     * Free-form narrative completion (FR-INSIGHT-02). Reuses the same engine
     * as extraction but returns the raw text instead of parsed JSON.
     */
    open override suspend fun generateText(prompt: String): String? {
        if (!ensureInitialized()) return null
        return generateSync(prompt)?.trim()
    }

    /**
     * Runs one stateless turn: a fresh conversation per call, so no history
     * leaks between notifications. `sendMessage` is synchronous on the
     * LiteRT-LM API, unlike the old MediaPipe callback+latch dance.
     */
    private suspend fun generateSync(prompt: String): String? {
        val activeEngine = engine ?: return null
        return withContext(Dispatchers.IO) {
            try {
                activeEngine.createConversation(ConversationConfig(samplerConfig = samplerConfig)).use { conversation ->
                    conversation.sendMessage(prompt).text()
                }
            } catch (e: Exception) {
                Log.e(TAG, "LiteRT-LM generation failed", e)
                null
            }
        }
    }

    /** Concatenates every text part of a response; the model always answers in plain text. */
    private fun Message.text(): String? =
        contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }.ifBlank { null }

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

    // Minimal input — matches the SFT dataset this model was fine-tuned on
    // exactly (see DatasetExportTest.kt's buildMinimalPrompt). Fine-tuning
    // bakes the extraction rules into the weights instead of re-explaining
    // them every call, so this model gets the short form; GeminiNanoExtractor
    // (never fine-tuned, can't be) keeps the full zero-shot instruction prompt.
    private fun buildPrompt(packageName: String, title: String, body: String): String {
        return "Categories: $CATEGORY_LIST\nNotification (pkg=$packageName): $title $body\nJSON:"
    }

    fun close() {
        engine?.close()
        engine = null
    }

    companion object {
        private const val TAG = "MediaPipeExtractor"
        private const val CATEGORY_LIST = "Food & Dining, Groceries, Transportation, Shopping, Bills & Utilities, " +
            "Rent, Entertainment, Health & Medical, Investment, Friend/Transfer, Salary/Income, Uncategorized"
    }
}
