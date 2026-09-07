package com.filemanager.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Insert
    suspend fun insert(entity: VaultItemEntity): Long

    @Query("SELECT * FROM vault_items ORDER BY addedAtEpochMillis DESC")
    fun observeAll(): Flow<List<VaultItemEntity>>

    @Delete
    suspend fun delete(entity: VaultItemEntity)
}
