package com.expensetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.expensetracker.core.database.entity.ClarificationHistoryEntity

@Dao
interface ClarificationHistoryDao {

    @Insert
    suspend fun insert(entity: ClarificationHistoryEntity): Long

    @Query("SELECT * FROM clarification_history ORDER BY answered_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<ClarificationHistoryEntity>

    @Query("SELECT COUNT(*) FROM clarification_history")
    suspend fun count(): Int
}
