package com.parwar.german_learning.data.dao

import androidx.room.*
import com.parwar.german_learning.data.models.StudyMode
import com.parwar.german_learning.data.models.StudySession
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions WHERE mode = :mode ORDER BY startTime DESC")
    fun getSessionsByMode(mode: StudyMode): Flow<List<StudySession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySession): Long

    @Update
    suspend fun updateSession(session: StudySession)

    @Delete
    suspend fun deleteSession(session: StudySession)

    @Query("SELECT AVG(CAST(correctAnswers AS FLOAT) / CAST(cardsReviewed AS FLOAT) * 100) FROM study_sessions WHERE cardsReviewed > 0")
    fun getAverageAccuracy(): Flow<Float>

    @Query("SELECT SUM(cardsReviewed) FROM study_sessions")
    fun getTotalCardsReviewed(): Flow<Int>

    @Query("SELECT SUM(correctAnswers) FROM study_sessions")
    fun getTotalCorrectAnswers(): Flow<Int>
}
