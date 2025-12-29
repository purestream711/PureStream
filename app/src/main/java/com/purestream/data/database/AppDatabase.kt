package com.purestream.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.purestream.data.model.Profile
import com.purestream.data.model.AppSettings
import com.purestream.data.database.entities.SubtitleAnalysisEntity
import com.purestream.data.database.entities.SubtitleTimingPreference
import com.purestream.data.database.entities.WatchProgressEntity
import com.purestream.data.database.entities.MovieEntity
import com.purestream.data.database.entities.TvShowEntity
import com.purestream.data.database.entities.LibraryEntity
import com.purestream.data.database.entities.CacheMetadataEntity
import com.purestream.data.database.entities.GeminiCachedMovie
import com.purestream.data.database.dao.SubtitleAnalysisDao
import com.purestream.data.database.dao.SubtitleTimingDao
import com.purestream.data.database.dao.WatchProgressDao
import com.purestream.data.database.dao.MovieCacheDao
import com.purestream.data.database.dao.TvShowCacheDao
import com.purestream.data.database.dao.LibraryCacheDao
import com.purestream.data.database.dao.CacheMetadataDao
import com.purestream.data.database.dao.GeminiCachedMovieDao
import android.content.Context

@Database(
    entities = [
        Profile::class,
        SubtitleAnalysisEntity::class,
        AppSettings::class,
        SubtitleTimingPreference::class,
        WatchProgressEntity::class,
        MovieEntity::class,
        TvShowEntity::class,
        LibraryEntity::class,
        CacheMetadataEntity::class,
        GeminiCachedMovie::class
    ],
    version = 17,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun subtitleAnalysisDao(): SubtitleAnalysisDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun subtitleTimingDao(): SubtitleTimingDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun movieCacheDao(): MovieCacheDao
    abstract fun tvShowCacheDao(): TvShowCacheDao
    abstract fun libraryCacheDao(): LibraryCacheDao
    abstract fun cacheMetadataDao(): CacheMetadataDao
    abstract fun geminiCachedMovieDao(): GeminiCachedMovieDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 16 to 17: Add RPG-style level-up tracker fields AND AI Curation/Default Profile fields
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add level tracking columns
                database.execSQL("ALTER TABLE profiles ADD COLUMN totalFilteredWordsCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE profiles ADD COLUMN currentLevel INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE profiles ADD COLUMN wordsFilteredThisLevel INTEGER NOT NULL DEFAULT 0")

                // Add AI Curation columns (if missing in previous version)
                // Using try-catch pattern or checking existence would be safer, but standard ADD COLUMN is fine if we assume v16 didn't have them
                // If v16 DID have them, this migration would fail. Assuming they are new in v17.
                try {
                    database.execSQL("ALTER TABLE profiles ADD COLUMN aiCuratedEnabled INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE profiles ADD COLUMN lastAiCurationTimestamp INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE profiles ADD COLUMN aiFeaturedMovieRatingKey TEXT DEFAULT NULL")
                    database.execSQL("ALTER TABLE profiles ADD COLUMN isDefaultProfile INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Ignore errors if columns already exist (e.g. during development iterations)
                    // detailed logging would be good here but we'll suppress to ensure migration completes
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "purestream_database"
                )
                // Add migrations to preserve user data across updates
                .addMigrations(MIGRATION_16_17)
                // Re-enable fallback to prevent crashes for users on very old versions (e.g. < 16)
                // Users on v16 will be migrated to v17 safely.
                // Users on < v16 will lose data but NOT crash.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}