package com.parwar.german_learning.utils

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.parwar.german_learning.data.models.ChatMessage
import com.parwar.german_learning.data.models.ChatMode
import com.parwar.german_learning.data.models.DialogPair
import com.parwar.german_learning.data.models.StudyMode
import com.parwar.german_learning.data.models.TestMode

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(value, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromStudyMode(value: StudyMode): String {
        return value.name
    }

    @TypeConverter
    fun toStudyMode(value: String): StudyMode {
        return try {
            StudyMode.valueOf(value)
        } catch (e: Exception) {
            StudyMode.NORMAL
        }
    }

    @TypeConverter
    fun fromTestMode(value: TestMode?): String? {
        return value?.name
    }

    @TypeConverter
    fun toTestMode(value: String?): TestMode? {
        return value?.let { TestMode.valueOf(it) }
    }

    @TypeConverter
    fun fromChatMessageList(value: List<ChatMessage>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toChatMessageList(value: String): List<ChatMessage> {
        val listType = object : TypeToken<List<ChatMessage>>() {}.type
        return try {
            gson.fromJson(value, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromChatMode(value: ChatMode): String {
        return value.name
    }

    @TypeConverter
    fun toChatMode(value: String): ChatMode {
        return try {
            ChatMode.valueOf(value)
        } catch (e: Exception) {
            ChatMode.GENERAL
        }
    }

    @TypeConverter
    fun fromDialogPairList(value: List<DialogPair>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toDialogPairList(value: String): List<DialogPair> {
        val listType = object : TypeToken<List<DialogPair>>() {}.type
        return try {
            gson.fromJson(value, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
