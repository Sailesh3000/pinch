package com.expensetracker.ingestion

import java.security.MessageDigest

/**
 * Deterministic structural hash for a transaction (FR-DEDUP-02).
 * Shared by the dedup engine (runtime matching) and the persisted
 * `dedup_hash` column on `transactions`.
 */
object DedupKey {

    fun compute(amount: Double, currency: String, payeeNorm: String?, timestampEpoch: Long): String {
        val windowAligned = timestampEpoch - (timestampEpoch % 180_000L)
        val material = listOf(
            "%.2f".format(amount),
            currency.uppercase(),
            payeeNorm?.takeIf { it.isNotBlank() } ?: "",
            windowAligned.toString(),
        ).joinToString("|")
        return sha256(material)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
