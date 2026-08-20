package com.expensetracker.extraction

import com.expensetracker.core.database.dao.TemplateCacheDao
import com.expensetracker.core.database.entity.TemplateCacheEntity

/**
 * In-memory fake for TemplateCacheDao used by TemplateCacheEngineTest.
 */
class FakeTemplateCacheDao : TemplateCacheDao {

    private val entries = mutableListOf<TemplateCacheEntity>()

    override suspend fun findByPattern(packageName: String, patternHash: String): TemplateCacheEntity? =
        entries.firstOrNull { it.packageName == packageName && it.patternHash == patternHash }

    override suspend fun upsert(entity: TemplateCacheEntity): Long {
        val existingIdx = entries.indexOfFirst { it.packageName == entity.packageName && it.patternHash == entity.patternHash }
        if (existingIdx >= 0) {
            entries[existingIdx] = entity.copy(id = entries[existingIdx].id)
            return entries[existingIdx].id
        }
        val newId = (entries.maxOfOrNull { it.id } ?: 0L) + 1
        entries.add(entity.copy(id = newId))
        return newId
    }

    override suspend fun recordHit(id: Long, nowEpochMillis: Long) {
        val idx = entries.indexOfFirst { it.id == id }
        if (idx >= 0) {
            entries[idx] = entries[idx].copy(
                hitCount = entries[idx].hitCount + 1,
                lastHitTimestamp = nowEpochMillis,
            )
        }
    }

    override suspend fun getAll(): List<TemplateCacheEntity> = entries.toList()

    override suspend fun deleteById(id: Long) {
        entries.removeAll { it.id == id }
    }
}
