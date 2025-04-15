package com.parwar.german_learning.data.dao

import androidx.room.*
import com.parwar.german_learning.data.models.SavedChat
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedChatDao {
    @Query("SELECT * FROM saved_chats ORDER BY timestamp DESC")
    fun getAllSavedChats(): Flow<List<SavedChat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedChat(savedChat: SavedChat)

    @Delete
    suspend fun deleteSavedChat(savedChat: SavedChat)

    @Query("SELECT * FROM saved_chats WHERE id = :chatId")
    suspend fun getSavedChatById(chatId: String): SavedChat?
}
