package com.expensetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.expensetracker.core.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY usage_count DESC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAllOnce(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CategoryEntity?

    @Insert
    suspend fun insertAll(entities: List<CategoryEntity>)

    @Query("UPDATE categories SET usage_count = usage_count + 1 WHERE id = :categoryId")
    suspend fun incrementUsage(categoryId: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
