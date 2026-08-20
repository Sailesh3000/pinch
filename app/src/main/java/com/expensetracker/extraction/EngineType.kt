package com.expensetracker.extraction

/**
 * Identifies which on-device extraction engine is active (FR-EXTRACT-02).
 * Routing priority: GEMINI_NANO → MEDIAPIPE → REGEX.
 */
enum class EngineType(val displayName: String) {
    GEMINI_NANO("Gemini Nano (Hardware Accelerated)"),
    MEDIAPIPE("MediaPipe Qwen2.5-0.5B (Local Engine)"),
    REGEX("Deterministic Regex Engine"),
}
