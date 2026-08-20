package com.expensetracker.ingestion

import com.expensetracker.core.database.entity.MonitoredPackageEntity
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory fast-path whitelist (FR-INGEST-02). The fast path must never touch
 * disk; the repository refreshes this snapshot whenever `monitored_packages`
 * changes. Defaults are seeded in `SeedData`.
 */
class PackageWhitelist {

    private val enabled = ConcurrentHashMap.newKeySet<String>()
    private val allLabels = ConcurrentHashMap<String, String>()

    fun refresh(packages: List<MonitoredPackageEntity>) {
        val nextEnabled = ConcurrentHashMap.newKeySet<String>()
        val nextLabels = ConcurrentHashMap<String, String>()
        for (p in packages) {
            nextLabels[p.packageName] = p.appLabel
            if (p.isEnabled) nextEnabled += p.packageName
        }
        enabled.clear()
        enabled.addAll(nextEnabled)
        allLabels.clear()
        allLabels.putAll(nextLabels)
    }

    fun isWhitelisted(packageName: String): Boolean = packageName in enabled

    fun labelFor(packageName: String): String? = allLabels[packageName]

    fun snapshot(): Set<String> = enabled.toSet()
}
