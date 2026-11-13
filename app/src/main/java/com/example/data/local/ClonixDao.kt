package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClonixDao {
    @Query("SELECT * FROM cloned_apps ORDER BY COALESCE(lastLaunchedAt, createdAt) DESC")
    fun observeClonedApps(): Flow<List<ClonedAppEntity>>

    @Query("SELECT * FROM cloned_apps WHERE id = :id LIMIT 1")
    suspend fun getCloneById(id: String): ClonedAppEntity?

    @Query("SELECT COUNT(*) FROM cloned_apps WHERE packageName = :packageName")
    suspend fun countInstancesForPackage(packageName: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClone(entity: ClonedAppEntity)

    @Update
    suspend fun updateClone(entity: ClonedAppEntity)

    @Query("UPDATE cloned_apps SET lastLaunchedAt = :timestamp, sandboxSizeBytes = :sizeBytes WHERE id = :id")
    suspend fun updateLaunchStats(id: String, timestamp: Long, sizeBytes: Long)

    @Query("UPDATE cloned_apps SET isLocked = :locked WHERE id = :id")
    suspend fun setCloneLocked(id: String, locked: Boolean)

    @Query("DELETE FROM cloned_apps WHERE id = :id")
    suspend fun deleteCloneById(id: String)

    @Query("DELETE FROM cloned_apps")
    suspend fun deleteAllClones()

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 50")
    fun observeAuditLogs(): Flow<List<SandboxAuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: SandboxAuditLogEntity)

    @Query("DELETE FROM audit_logs")
    suspend fun clearAuditLogs()
}
