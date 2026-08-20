package com.expensetracker.extraction

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Structured extraction output mirroring the spec §6.1 `ExpenseExtractionResult`
 * JSON schema. Produced 100% on-device; zero network.
 */
@Serializable
data class ExtractionResult(
    @SerialName("is_financial_transaction")
    val isFinancialTransaction: Boolean,
    val amount: Double,
    val currency: String = "INR",
    @SerialName("txn_type")
    val txnType: String = "UNKNOWN",
    @SerialName("merchant_or_payee")
    val merchantOrPayee: String = "",
    @SerialName("account_reference")
    val accountReference: String? = null,
    val category: String = "Uncategorized",
    @SerialName("confidence_score")
    val confidenceScore: Float = 0.5f,
    val reasoning: String? = null,
)
