package com.parwar.german_learning.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.parwar.german_learning.utils.Converters

@Entity(tableName = "study_sessions")
@TypeConverters(Converters::class)
data class StudySession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val cardsReviewed: Int = 0,
    val correctAnswers: Int = 0,
    val wrongAnswers: Int = 0,
    val mode: StudyMode = StudyMode.NORMAL,
    val testMode: TestMode? = null,
    val wrongAnswerDetails: String = "", // Stores detailed information about wrong answers
    val averageResponseTime: Long = 0,   // Average time taken to answer questions
    val totalStudyTime: Long = 0         // Total time spent in this session
) {
    val accuracy: Float
        get() = if (cardsReviewed > 0) (correctAnswers.toFloat() / cardsReviewed) * 100 else 0f
        
    val formattedStudyTime: String
        get() {
            val minutes = totalStudyTime / 60000
            val seconds = (totalStudyTime % 60000) / 1000
            return "${minutes}m ${seconds}s"
        }
}

enum class StudyMode {
    NORMAL,
    GYM,
    QUIZ,
    CONVERSATION
}
