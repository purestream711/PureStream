# Room Database Migration Guide for PureStream

## Current Version: 16

## Why Migrations Matter
Without proper migrations, users lose ALL their data (profiles, watch progress, settings) when they update the app. Migrations preserve user data by transforming the old database schema to the new one.

## How to Add a Migration (Step-by-Step)

### Example: Adding a new field to Profile table

**Step 1: Modify your data model (e.g., Profile.kt)**
```kotlin
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatarResourceName: String,
    val isKidsProfile: Boolean = false,
    val filterLevel: ProfanityFilterLevel = ProfanityFilterLevel.NONE,
    // ... other existing fields ...

    // NEW FIELD ADDED
    val favoriteGenre: String = "Action"  // Must have default value
)
```

**Step 2: Increment database version in AppDatabase.kt**
```kotlin
@Database(
    entities = [...],
    version = 17,  // Increment from 16 to 17
    exportSchema = false
)
```

**Step 3: Define migration in AppDatabase.kt companion object**
```kotlin
companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    // Migration from version 16 to 17
    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add new column with default value
            database.execSQL(
                "ALTER TABLE profiles ADD COLUMN favoriteGenre TEXT NOT NULL DEFAULT 'Action'"
            )
        }
    }

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "purestream_database"
            )
            .addMigrations(MIGRATION_16_17)  // Add your migration here!
            .fallbackToDestructiveMigration()  // Keep as safety net
            .build()
            INSTANCE = instance
            instance
        }
    }
}
```

## Common Migration Scenarios

### Adding a new column
```kotlin
private val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE table_name ADD COLUMN new_column TEXT NOT NULL DEFAULT ''")
    }
}
```

### Adding a new table
```kotlin
private val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS new_table (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                value INTEGER NOT NULL
            )
        """.trimIndent())
    }
}
```

### Renaming a column
```kotlin
private val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // SQLite doesn't support RENAME COLUMN directly, need to recreate table
        database.execSQL("ALTER TABLE table_name RENAME TO table_name_old")
        database.execSQL("""
            CREATE TABLE table_name (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                new_column_name TEXT NOT NULL
            )
        """.trimIndent())
        database.execSQL("INSERT INTO table_name SELECT id, old_column_name FROM table_name_old")
        database.execSQL("DROP TABLE table_name_old")
    }
}
```

### Deleting a column
```kotlin
private val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // SQLite doesn't support DROP COLUMN, need to recreate table
        database.execSQL("ALTER TABLE table_name RENAME TO table_name_old")
        database.execSQL("""
            CREATE TABLE table_name (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                kept_column TEXT NOT NULL
            )
        """.trimIndent())
        database.execSQL("INSERT INTO table_name SELECT id, kept_column FROM table_name_old")
        database.execSQL("DROP TABLE table_name_old")
    }
}
```

## Multiple Migrations (Chain)

If you need to support users upgrading from multiple old versions:

```kotlin
private val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE profiles ADD COLUMN field1 TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE profiles ADD COLUMN field2 TEXT NOT NULL DEFAULT ''")
    }
}

// In getDatabase():
.addMigrations(MIGRATION_16_17, MIGRATION_17_18)
```

Room will automatically chain these migrations if a user jumps from version 16 to 18.

## Testing Migrations

Always test migrations before releasing:

```kotlin
// In your test class
@Test
fun testMigration16to17() {
    val testDb = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().context,
        AppDatabase::class.java
    )
    .addMigrations(MIGRATION_16_17)
    .build()

    // Verify migration worked
    testDb.query("SELECT * FROM profiles").use { cursor ->
        assertTrue(cursor.columnNames.contains("favoriteGenre"))
    }
}
```

## Important Notes

1. **Always increment the version number** when changing schema
2. **Always provide default values** for new fields in migrations
3. **Test migrations** before releasing to production
4. **Keep `.fallbackToDestructiveMigration()`** as a safety net for unknown old versions
5. **Document what changed** in each migration for future reference

## Current Database Schema (Version 16)

Tables:
- `profiles` - User profiles with filter settings
- `subtitle_analysis` - Cached profanity analysis results
- `app_settings` - Global app settings
- `subtitle_timing_preferences` - Per-content subtitle timing
- `watch_progress` - User watch history
- `movies` - Cached movie data
- `tv_shows` - Cached TV show data
- `libraries` - Cached Plex libraries
- `cache_metadata` - Cache invalidation metadata
- `gemini_cached_movies` - Gemini AI movie suggestions

## Migration History

### Version 16 (Current)
- Users upgrading to v16 will lose data (no migration path from previous versions)
- Going forward, all migrations from v16+ will preserve data

### Future Versions
- Add migrations here as you create them
- Document what changed in each version
