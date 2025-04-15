package com.parwar.german_learning.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parwar.german_learning.data.dao.StudySessionDao
import com.parwar.german_learning.data.models.StudySession
import com.parwar.german_learning.data.models.TestMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgressStats(
    val totalCardsReviewed: Int = 0,
    val totalCorrectAnswers: Int = 0,
    val averageAccuracy: Float = 0f,
    val listeningStats: TestTypeStats = TestTypeStats(),
    val readingStats: TestTypeStats = TestTypeStats(),
    val writingStats: TestTypeStats = TestTypeStats()
)

data class TestTypeStats(
    val cardsReviewed: Int = 0,
    val correctAnswers: Int = 0,
    val accuracy: Float = 0f
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val studySessionDao: StudySessionDao
) : ViewModel() {

    private val _stats = MutableStateFlow(ProgressStats())
    val stats: StateFlow<ProgressStats> = _stats.asStateFlow()

    private val _sessions = MutableStateFlow<List<StudySession>>(emptyList())
    val sessions: StateFlow<List<StudySession>> = _sessions.asStateFlow()

    init {
        loadStats()
        loadSessions()
    }

    private fun loadStats() {
        viewModelScope.launch {
            combine(
                studySessionDao.getTotalCardsReviewed(),
                studySessionDao.getTotalCorrectAnswers(),
                studySessionDao.getAverageAccuracy(),
                studySessionDao.getAllSessions()
            ) { totalReviewed, totalCorrect, avgAccuracy, sessions ->
                val listeningStats = calculateTestTypeStats(sessions, TestMode.LISTENING)
                val readingStats = calculateTestTypeStats(sessions, TestMode.READING)
                val writingStats = calculateTestTypeStats(sessions, TestMode.WRITING)

                ProgressStats(
                    totalCardsReviewed = totalReviewed,
                    totalCorrectAnswers = totalCorrect,
                    averageAccuracy = avgAccuracy,
                    listeningStats = listeningStats,
                    readingStats = readingStats,
                    writingStats = writingStats
                )
            }.collect { stats ->
                _stats.value = stats
            }
        }
    }

    private fun loadSessions() {
        viewModelScope.launch {
            studySessionDao.getAllSessions().collect { sessions ->
                _sessions.value = sessions
            }
        }
    }

    fun deleteSession(session: StudySession) {
        viewModelScope.launch {
            studySessionDao.deleteSession(session)
        }
    }

    private fun calculateTestTypeStats(sessions: List<StudySession>, testMode: TestMode): TestTypeStats {
        val testSessions = sessions.filter { it.testMode == testMode }
        val cardsReviewed = testSessions.sumOf { it.cardsReviewed }
        val correctAnswers = testSessions.sumOf { it.correctAnswers }
        val accuracy = if (cardsReviewed > 0) {
            (correctAnswers.toFloat() / cardsReviewed.toFloat()) * 100
        } else {
            0f
        }
        return TestTypeStats(
            cardsReviewed = cardsReviewed,
            correctAnswers = correctAnswers,
            accuracy = accuracy
        )
    }
}
