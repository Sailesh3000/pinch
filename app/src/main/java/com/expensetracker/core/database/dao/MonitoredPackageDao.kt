package com.expensetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.expensetracker.core.database.entity.MonitoredPackageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredPackageDao {

    @Query("SELECT * FROM monitored_packages ORDER BY app_label ASC")
    fun observeAll(): Flow<List<MonitoredPackageEntity>>

    @Query("SELECT * FROM monitored_packages")
    suspend fun getAllOnce(): List<MonitoredPackageEntity>

    @Query("SELECT * FROM monitored_packages WHERE is_enabled = 1")
    suspend fun getEnabled(): List<MonitoredPackageEntity>

    @Query("SELECT * FROM monitored_packages WHERE package_name = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): MonitoredPackageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<MonitoredPackageEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: MonitoredPackageEntity): Long

    @Update
    suspend fun update(entity: MonitoredPackageEntity)

    @Query("DELETE FROM monitored_packages WHERE id = :id")
    suspend fun deleteById(id: Long)
}
