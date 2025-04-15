package com.parwar.german_learning.data

import android.content.Context
import android.os.Environment
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.parwar.german_learning.data.dao.DialogDao
import com.parwar.german_learning.data.dao.FlashCardDao
import com.parwar.german_learning.data.dao.StudySessionDao
import com.parwar.german_learning.data.models.Dialog
import com.parwar.german_learning.data.models.FlashCard
import com.parwar.german_learning.data.models.StudySession
import com.parwar.german_learning.utils.Converters
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@Database(
    entities = [FlashCard::class, Dialog::class, StudySession::class],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun flashCardDao(): FlashCardDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun dialogDao(): DialogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private var useExternalStorage = false

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop and recreate the dialogs table with the new schema
                db.execSQL("DROP TABLE IF EXISTS dialogs")
                db.execSQL("""
                    CREATE TABLE dialogs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        germanText TEXT NOT NULL,
                        englishText TEXT NOT NULL,
                        participants TEXT NOT NULL,
                        tags TEXT NOT NULL,
                        category TEXT NOT NULL,
                        difficulty TEXT NOT NULL,
                        contextNotes TEXT NOT NULL
                    )
                """)
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add dialogPairs column with default empty list
                db.execSQL("ALTER TABLE dialogs ADD COLUMN dialogPairs TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS flashcards_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL DEFAULT 'WORD',
                        germanText TEXT NOT NULL,
                        englishText TEXT NOT NULL,
                        phonetic TEXT NOT NULL,
                        tags TEXT NOT NULL,
                        examples TEXT NOT NULL,
                        lastReviewed INTEGER NOT NULL,
                        reviewCount INTEGER NOT NULL,
                        difficulty REAL NOT NULL,
                        nextReviewDate INTEGER NOT NULL,
                        grammarNotes TEXT,
                        audioPath TEXT,
                        relatedWords TEXT NOT NULL DEFAULT '[]',
                        contextNotes TEXT,
                        category TEXT
                    )
                """)

                db.execSQL("""
                    INSERT INTO flashcards_new (
                        id, germanText, englishText, phonetic, tags, examples,
                        lastReviewed, reviewCount, difficulty, nextReviewDate,
                        grammarNotes, audioPath
                    )
                    SELECT 
                        id, germanText, englishText, phonetic, tags, examples,
                        lastReviewed, reviewCount, difficulty, nextReviewDate,
                        grammarNotes, audioPath
                    FROM flashcards
                """)

                db.execSQL("DROP TABLE flashcards")
                db.execSQL("ALTER TABLE flashcards_new RENAME TO flashcards")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS study_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER,
                        cardsReviewed INTEGER NOT NULL DEFAULT 0,
                        correctAnswers INTEGER NOT NULL DEFAULT 0,
                        wrongAnswers INTEGER NOT NULL DEFAULT 0,
                        mode TEXT NOT NULL DEFAULT 'NORMAL'
                    )
                """)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS study_sessions")
                db.execSQL("""
                    CREATE TABLE study_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER,
                        cardsReviewed INTEGER NOT NULL DEFAULT 0,
                        correctAnswers INTEGER NOT NULL DEFAULT 0,
                        wrongAnswers INTEGER NOT NULL DEFAULT 0,
                        mode TEXT NOT NULL DEFAULT 'NORMAL'
                    )
                """)
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to study_sessions table
                db.execSQL("""
                    ALTER TABLE study_sessions ADD COLUMN testMode TEXT DEFAULT NULL
                """)
                db.execSQL("""
                    ALTER TABLE study_sessions ADD COLUMN wrongAnswerDetails TEXT NOT NULL DEFAULT ''
                """)
                db.execSQL("""
                    ALTER TABLE study_sessions ADD COLUMN averageResponseTime INTEGER NOT NULL DEFAULT 0
                """)
                db.execSQL("""
                    ALTER TABLE study_sessions ADD COLUMN totalStudyTime INTEGER NOT NULL DEFAULT 0
                """)
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Removed SavedChat from AppDatabase since it's now in its own database
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop the saved_chats table since it's moved to a separate database
                database.execSQL("DROP TABLE IF EXISTS saved_chats")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `dialogs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `germanText` TEXT NOT NULL,
                        `englishText` TEXT NOT NULL,
                        `participants` TEXT NOT NULL,
                        `tags` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `difficulty` TEXT NOT NULL,
                        `contextNotes` TEXT NOT NULL
                    )
                """)
            }
        }

        private fun isExternalStorageWritable(): Boolean {
            return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        }

        private fun copyDatabaseToExternalStorage(context: Context, dbFile: File): Boolean {
            val internalDbFile = context.getDatabasePath("german_learning_db")
            if (!internalDbFile.exists()) return false

            return try {
                FileInputStream(internalDbFile).use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Copy WAL file if it exists
                val walFile = File(internalDbFile.parent, "german_learning_db-wal")
                if (walFile.exists()) {
                    val externalWalFile = File(dbFile.parent, "german_learning_db-wal")
                    FileInputStream(walFile).use { input ->
                        FileOutputStream(externalWalFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Copy SHM file if it exists
                val shmFile = File(internalDbFile.parent, "german_learning_db-shm")
                if (shmFile.exists()) {
                    val externalShmFile = File(dbFile.parent, "german_learning_db-shm")
                    FileInputStream(shmFile).use { input ->
                        FileOutputStream(externalShmFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Only delete internal files if external copy was successful
                internalDbFile.delete()
                if (walFile.exists()) walFile.delete()
                if (shmFile.exists()) shmFile.delete()

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        private fun getExternalDatabasePath(context: Context): String {
            val dbFolder = File(
                Environment.getExternalStorageDirectory(),
                "GermanLearning"
            )
            if (!dbFolder.exists() && !dbFolder.mkdirs()) {
                // If we can't create external directory, fall back to internal storage
                throw SecurityException("Cannot create external directory")
            }

            val dbFile = File(dbFolder, "german_learning_db")
            if (!dbFile.exists() && !copyDatabaseToExternalStorage(context, dbFile)) {
                // If we can't copy database to external storage, fall back to internal
                throw SecurityException("Cannot copy database to external storage")
            }

            return dbFile.absolutePath
        }

        fun getDatabase(context: Context, useExternal: Boolean = false): AppDatabase {
            useExternalStorage = useExternal
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    if (useExternalStorage) getExternalDatabasePath(context) else "german_learning_db"
                )
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
