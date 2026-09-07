package com.filemanager.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RecycleBinEntity::class, VaultItemEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recycleBinDao(): RecycleBinDao
    abstract fun vaultDao(): VaultDao
}
