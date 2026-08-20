package com.expensetracker.data.repository

import com.expensetracker.core.database.dao.MonitoredPackageDao
import com.expensetracker.core.database.entity.MonitoredPackageEntity
import com.expensetracker.core.model.MonitoredPackage
import com.expensetracker.ingestion.PackageWhitelist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitoredPackageRepository @Inject constructor(
    private val dao: MonitoredPackageDao,
    private val whitelist: PackageWhitelist,
) {

    fun observeAll(): Flow<List<MonitoredPackage>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun refreshWhitelistCache() {
        whitelist.refresh(dao.getAllOnce())
    }

    suspend fun addCustomPackage(packageName: String, appLabel: String? = null): Boolean {
        val exists = dao.getByPackageName(packageName)
        if (exists != null) return false
        dao.insert(MonitoredPackageEntity(packageName = packageName, appLabel = appLabel ?: packageName))
        refreshWhitelistCache()
        return true
    }

    suspend fun setEnabled(packageName: String, enabled: Boolean) {
        val entity = dao.getByPackageName(packageName) ?: return
        dao.update(entity.copy(isEnabled = enabled))
        refreshWhitelistCache()
    }

    suspend fun removePackage(id: Long) {
        dao.deleteById(id)
        refreshWhitelistCache()
    }

    private fun MonitoredPackageEntity.toDomain(): MonitoredPackage = MonitoredPackage(
        id = id,
        packageName = packageName,
        appLabel = appLabel,
        isEnabled = isEnabled,
        addedAt = addedAt,
    )
}
