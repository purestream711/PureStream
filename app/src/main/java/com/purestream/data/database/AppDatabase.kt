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
    version = 20,
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

                // Add AI Curation columns
                try {
                    database.execSQL("ALTER TABLE profiles ADD COLUMN aiCuratedEnabled INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE profiles ADD COLUMN lastAiCurationTimestamp INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE profiles ADD COLUMN aiFeaturedMovieRatingKey TEXT DEFAULT NULL")
                    database.execSQL("ALTER TABLE profiles ADD COLUMN isDefaultProfile INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Ignore if columns already exist
                }
            }
        }

        // Migration from version 17 to 18: Add unlockedAchievements
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE profiles ADD COLUMN unlockedAchievements TEXT NOT NULL DEFAULT '[]'")
            }
        }

        // Migration from version 18 to 19: Add preferred library columns to profiles
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE profiles ADD COLUMN preferredMovieLibraryId TEXT DEFAULT NULL")
                    database.execSQL("ALTER TABLE profiles ADD COLUMN preferredTvShowLibraryId TEXT DEFAULT NULL")
                    database.execSQL("ALTER TABLE profiles ADD COLUMN preferredDashboardLibraryId TEXT DEFAULT NULL")
                } catch (e: Exception) {
                    // Columns might already exist in some dev environments
                }
            }
        }

        // Migration from version 19 to 20: Add Gemini cached movies table
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `gemini_cached_movies` (
                        `id` TEXT NOT NULL, 
                        `profileId` TEXT NOT NULL, 
                        `collectionId` TEXT NOT NULL, 
                        `movieRatingKey` TEXT NOT NULL, 
                        `order` INTEGER NOT NULL, 
                        `cachedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "purestream_database"
                )
                // Add all migrations to ensure data preservation across versions
                .addMigrations(
                    MIGRATION_16_17, 
                    MIGRATION_17_18, 
                    MIGRATION_18_19, 
                    MIGRATION_19_20
                )
                // Keep fallback only as a last resort for extremely old versions
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}