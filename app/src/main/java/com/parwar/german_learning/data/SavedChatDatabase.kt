package com.parwar.german_learning.data

import android.content.Context
import android.os.Environment
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.parwar.german_learning.data.dao.SavedChatDao
import com.parwar.german_learning.data.models.SavedChat
import com.parwar.german_learning.utils.Converters
import java.io.File

@Database(
    entities = [SavedChat::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SavedChatDatabase : RoomDatabase() {
    abstract fun savedChatDao(): SavedChatDao

    companion object {
        @Volatile
        private var INSTANCE: SavedChatDatabase? = null

        private fun getExternalDatabasePath(context: Context): String {
            val externalDir = File(Environment.getExternalStorageDirectory(), "germanlearning/savedchat")
            if (!externalDir.exists()) {
                externalDir.mkdirs()
            }
            return "${externalDir.absolutePath}/saved_chats.db"
        }

        fun getDatabase(context: Context): SavedChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SavedChatDatabase::class.java,
                    getExternalDatabasePath(context)
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
