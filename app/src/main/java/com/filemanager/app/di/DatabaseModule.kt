package com.filemanager.app.di

import android.content.Context
import androidx.room.Room
import com.filemanager.app.data.local.AppDatabase
import com.filemanager.app.data.local.RecycleBinDao
import com.filemanager.app.data.local.VaultDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "filemanager.db")
            // MVP-stage schema; no user data lives only in this table (vault
            // files remain encrypted on disk regardless), so destructive
            // migration is an acceptable trade-off until the schema stabilizes.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRecycleBinDao(database: AppDatabase): RecycleBinDao = database.recycleBinDao()

    @Provides
    fun provideVaultDao(database: AppDatabase): VaultDao = database.vaultDao()
}
