package com.fenrir.her.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.fenrir.her.constants.MaxSongCacheSizeKey
import com.fenrir.her.db.InternalDatabase
import com.fenrir.her.db.MusicDatabase
import com.fenrir.her.listentogether.ListenTogetherClient
import com.fenrir.her.listentogether.ListenTogetherManager
import com.fenrir.her.utils.dataStore
import com.fenrir.her.utils.get
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

  @Provides
  @Singleton
  @ApplicationScope
  fun provideApplicationScope(): CoroutineScope {
    return CoroutineScope(SupervisorJob() + Dispatchers.Default)
  }

  @Singleton
  @Provides
  fun provideDao(
    database: InternalDatabase,
  ) = database.dao

  @Singleton
  @Provides
  fun provideDatabase(
    internalDatabase: InternalDatabase,
  ): MusicDatabase = MusicDatabase(internalDatabase)

  @Singleton
  @Provides
  fun provideInternalDatabase(
    @ApplicationContext context: Context,
  ): InternalDatabase =
    Room.databaseBuilder(context, InternalDatabase::class.java, InternalDatabase.DB_NAME)
      .addMigrations(
        com.fenrir.her.db.MIGRATION_1_2,
        com.fenrir.her.db.MIGRATION_21_24,
        com.fenrir.her.db.MIGRATION_22_24,
        com.fenrir.her.db.MIGRATION_24_25,
        com.fenrir.her.db.MIGRATION_27_28,
        com.fenrir.her.db.MIGRATION_28_29,
        com.fenrir.her.db.MIGRATION_29_30,
        com.fenrir.her.db.MIGRATION_31_32,
        com.fenrir.her.db.MIGRATION_36_37,
        com.fenrir.her.db.MIGRATION_37_38,
        com.fenrir.her.db.MIGRATION_38_39,
        com.fenrir.her.db.MIGRATION_39_40,
        com.fenrir.her.db.MIGRATION_40_41,
        com.fenrir.her.db.MIGRATION_41_42,
        com.fenrir.her.db.MIGRATION_42_43,
        com.fenrir.her.db.MIGRATION_43_44,
        com.fenrir.her.db.MIGRATION_44_45,
        com.fenrir.her.db.MIGRATION_45_46,
      )
      .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
      .setTransactionExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
      .setQueryExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
      .addCallback(
        object : androidx.room.RoomDatabase.Callback() {
          override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            super.onOpen(db)
            try {
              db.query("PRAGMA busy_timeout = 60000").close()
              db.query("PRAGMA cache_size = -16000").close()
              db.query("PRAGMA wal_autocheckpoint = 1000").close()
              db.query("PRAGMA synchronous = NORMAL").close()
            } catch (e: Exception) {
              timber.log.Timber.tag("MusicDatabase").e(e, "Failed to set PRAGMA settings")
            }
          }
        }
      )
      .build()

  @Singleton
  @Provides
  fun provideDatabaseProvider(
    @ApplicationContext context: Context,
  ): DatabaseProvider = StandaloneDatabaseProvider(context)

  @Singleton
  @Provides
  @PlayerCache
  fun providePlayerCache(
    @ApplicationContext context: Context,
    databaseProvider: DatabaseProvider,
  ): SimpleCache {
    val cacheSize = context.dataStore[MaxSongCacheSizeKey] ?: 1024
    return SimpleCache(
      context.filesDir.resolve("exoplayer"),
      when (cacheSize) {
        -1 -> NoOpCacheEvictor()
        else -> com.fenrir.her.playback.DynamicLruCacheEvictor(cacheSize * 1024 * 1024L)
      },
      databaseProvider,
    )
  }

  @Singleton
  @Provides
  @DownloadCache
  fun provideDownloadCache(
    @ApplicationContext context: Context,
    databaseProvider: DatabaseProvider,
  ): SimpleCache {
    return SimpleCache(context.filesDir.resolve("download"), NoOpCacheEvictor(), databaseProvider)
  }

  @Singleton
  @Provides
  fun provideListenTogetherClient(
    @ApplicationContext context: Context,
  ): ListenTogetherClient = ListenTogetherClient(context)

  @Singleton
  @Provides
  fun provideListenTogetherManager(
    @ApplicationContext context: Context,
    client: ListenTogetherClient,
  ): ListenTogetherManager = ListenTogetherManager(client, context)
}
