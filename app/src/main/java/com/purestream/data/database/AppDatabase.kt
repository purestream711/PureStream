package com.purestream.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.purestream.data.model.Profile
import com.purestream.data.model.AppSettings
import com.purestream.data.database.entities.SubtitleAnalysisEntity
import com.purestream.data.database.entities.SubtitleTimingPreference
import com.purestream.data.database.entities.WatchProgressEntity
import com.purestream.data.database.dao.SubtitleAnalysisDao
import com.purestream.data.database.dao.SubtitleTimingDao
import com.purestream.data.database.dao.WatchProgressDao
import android.content.Context

@Database(
    entities = [Profile::class, SubtitleAnalysisEntity::class, AppSettings::class, SubtitleTimingPreference::class, WatchProgressEntity::class],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun subtitleAnalysisDao(): SubtitleAnalysisDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun subtitleTimingDao(): SubtitleTimingDao
    abstract fun watchProgressDao(): WatchProgressDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "purestream_database"
                ).fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}