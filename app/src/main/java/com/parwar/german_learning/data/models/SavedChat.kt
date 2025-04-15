package com.parwar.german_learning.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.parwar.german_learning.utils.Converters

@Entity(tableName = "saved_chats")
@TypeConverters(Converters::class)
data class SavedChat(
    @PrimaryKey
    val id: String = System.currentTimeMillis().toString(),
    val name: String,
    val messages: List<ChatMessage>,
    val mode: ChatMode,
    val timestamp: Long = System.currentTimeMillis()
)
