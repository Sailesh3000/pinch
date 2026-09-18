package com.expensetracker.extraction

/**
 * How the on-device AI model is available to the app.
 *  - [BUNDLED]: shipped with Play Store installs via Play Asset Delivery.
 *  - [DOWNLOADED]: fetched separately (internal storage; sideloads/dev builds).
 *  - [UNAVAILABLE]: no model present — only regex extraction is possible.
 */
enum class ModelSource {
    BUNDLED,
    DOWNLOADED,
    UNAVAILABLE,
}