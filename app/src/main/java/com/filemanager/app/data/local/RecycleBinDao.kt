package com.filemanager.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecycleBinDao {

    @Insert
    suspend fun insert(entity: RecycleBinEntity): Long

    @Query("SELECT * FROM recycle_bin ORDER BY deletedAtEpochMillis DESC")
    fun observeAll(): Flow<List<RecycleBinEntity>>

    @Delete
    suspend fun delete(entity: RecycleBinEntity)

    @Query("DELETE FROM recycle_bin")
    suspend fun deleteAll()
}
