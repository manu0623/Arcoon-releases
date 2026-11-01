package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.ManagedInstallerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ManagedInstallerDao {

    @Query("SELECT * FROM managed_installers ORDER BY locked DESC, sortOrder ASC, displayName ASC")
    suspend fun getAll(): List<ManagedInstallerEntity>

    @Query("SELECT * FROM managed_installers ORDER BY locked DESC, sortOrder ASC, displayName ASC")
    fun observeAll(): Flow<List<ManagedInstallerEntity>>

    @Query("SELECT * FROM managed_installers WHERE id = :id")
    suspend fun getById(id: Long): ManagedInstallerEntity?

    @Query("SELECT * FROM managed_installers WHERE repoOwner = :owner AND repoName = :name")
    suspend fun getByRepo(owner: String, name: String): ManagedInstallerEntity?

    @Query("SELECT * FROM managed_installers WHERE packageName = :packageName")
    suspend fun getByPackage(packageName: String): ManagedInstallerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ManagedInstallerEntity): Long

    @Query("DELETE FROM managed_installers WHERE id = :id AND locked = 0")
    suspend fun deleteUnlocked(id: Long): Int
}
