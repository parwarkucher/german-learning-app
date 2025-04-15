package com.parwar.german_learning.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.parwar.german_learning.utils.Converters

@Entity(tableName = "flashcards")
@TypeConverters(Converters::class)
data class FlashCard(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: ContentType = ContentType.WORD,
    val germanText: String,
    val englishText: String,
    val phonetic: String,
    val tags: List<String>,
    val examples: List<String>,
    val lastReviewed: Long = System.currentTimeMillis(),
    val reviewCount: Int = 0,
    val difficulty: Float = 0.5f,
    val nextReviewDate: Long = System.currentTimeMillis(),
    val grammarNotes: String? = null,
    val audioPath: String? = null,
    val relatedWords: List<String> = emptyList(),
    val contextNotes: String? = null,
    val category: String? = null
)

enum class ContentType {
    WORD,
    PHRASE,
    GRAMMAR_RULE,
    CONVERSATION,
    CULTURAL_NOTE,
    IDIOM,
    BATCH_CARDS
}
