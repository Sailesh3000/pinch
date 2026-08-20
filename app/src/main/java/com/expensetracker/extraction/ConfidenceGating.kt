package com.expensetracker.extraction

/**
 * Confidence gating (FR-EXTRACT-04):
 * - >= 0.75  -> HIGH, auto-save silently.
 * - 0.50..0.75 -> MEDIUM, auto-save with best-guess category, needs_clarification = false.
 * - <  0.50  -> LOW, save with "Uncategorized", needs_clarification = true (review queue).
 */
object ConfidenceGating {

    const val HIGH_THRESHOLD = 0.75f
    const val MEDIUM_LOW_THRESHOLD = 0.50f

    data class GatedConfidence(
        val level: com.expensetracker.core.model.ConfidenceLevel,
        val needsClarification: Boolean,
    )

    fun apply(score: Float): GatedConfidence = when {
        score >= HIGH_THRESHOLD -> GatedConfidence(
            com.expensetracker.core.model.ConfidenceLevel.HIGH, false
        )
        score >= MEDIUM_LOW_THRESHOLD -> GatedConfidence(
            com.expensetracker.core.model.ConfidenceLevel.MEDIUM, false
        )
        else -> GatedConfidence(
            com.expensetracker.core.model.ConfidenceLevel.LOW, true
        )
    }
}
