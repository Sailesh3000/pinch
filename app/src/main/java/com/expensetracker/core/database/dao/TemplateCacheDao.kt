package com.expensetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.expensetracker.core.database.entity.TemplateCacheEntity

@Dao
interface TemplateCacheDao {

    @Query(
        """
        SELECT * FROM template_cache
        WHERE package_name = :packageName AND pattern_hash = :patternHash
        LIMIT 1
        """
    )
    suspend fun findByPattern(packageName: String, patternHash: String): TemplateCacheEntity?

    @Upsert
    suspend fun upsert(entity: TemplateCacheEntity): Long

    @Query(
        """
        UPDATE template_cache
        SET hit_count = hit_count + 1, last_hit_timestamp = :nowEpochMillis
        WHERE id = :id
        """
    )
    suspend fun recordHit(id: Long, nowEpochMillis: Long)

    @Query("SELECT * FROM template_cache ORDER BY hit_count DESC")
    suspend fun getAll(): List<TemplateCacheEntity>

    @Query("DELETE FROM template_cache WHERE id = :id")
    suspend fun deleteById(id: Long)
}
