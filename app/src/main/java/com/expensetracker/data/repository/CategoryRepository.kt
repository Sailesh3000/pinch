package com.expensetracker.data.repository

import com.expensetracker.core.database.dao.CategoryDao
import com.expensetracker.core.database.entity.CategoryEntity
import com.expensetracker.core.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
) {

    fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getByName(name: String): Category? =
        categoryDao.getByName(name)?.toDomain()

    suspend fun getAllOnce(): List<Category> =
        categoryDao.getAllOnce().map { it.toDomain() }

    suspend fun incrementUsage(categoryId: Long) = categoryDao.incrementUsage(categoryId)

    /**
     * Resolve the DB id for an extracted category name. Falls back to the
     * "Uncategorized" category (creating it on demand if absent).
     */
    suspend fun resolveCategoryId(categoryName: String, uncategorizedName: String = "Uncategorized"): Long {
        val byName = categoryDao.getByName(categoryName)
        if (byName != null) return byName.id
        val uncategorized = categoryDao.getByName(uncategorizedName)
        if (uncategorized != null) return uncategorized.id
        val created = categoryDao.insertAll(listOf(CategoryEntity(name = uncategorizedName, iconKey = "UNCATEGORIZED", colorHex = "#ADB5BD")))
        return categoryDao.getByName(uncategorizedName)?.id
            ?: categoryDao.getAllOnce().last().id
    }

    private fun CategoryEntity.toDomain(): Category = Category(
        id = id,
        name = name,
        iconKey = iconKey,
        colorHex = colorHex,
        isSystemDefault = isSystemDefault,
        usageCount = usageCount,
    )
}
