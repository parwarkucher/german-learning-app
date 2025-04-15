package com.parwar.german_learning.data.dao

import androidx.room.*
import com.parwar.german_learning.data.models.FlashCard
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashCardDao {
    @Query("SELECT * FROM flashcards")
    fun getAllFlashCards(): Flow<List<FlashCard>>

    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun getFlashCardById(id: Long): FlashCard?

    @Query("SELECT * FROM flashcards WHERE nextReviewDate <= :currentTime ORDER BY nextReviewDate ASC")
    fun getDueFlashCards(currentTime: Long = System.currentTimeMillis()): Flow<List<FlashCard>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashCard(flashCard: FlashCard): Long

    @Update
    suspend fun updateFlashCard(flashCard: FlashCard)

    @Delete
    suspend fun deleteFlashCard(flashCard: FlashCard)

    @Query("""
        SELECT * FROM flashcards 
        WHERE EXISTS (
            SELECT 1 
            FROM json_each(tags) 
            WHERE json_each.value = :tag
        )
    """)
    fun getFlashCardsByTag(tag: String): Flow<List<FlashCard>>
}
