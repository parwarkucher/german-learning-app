package com.parwar.german_learning.ui.screens.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parwar.german_learning.data.dao.DialogDao
import com.parwar.german_learning.data.dao.FlashCardDao
import com.parwar.german_learning.data.dao.StudySessionDao
import com.parwar.german_learning.data.models.*
import com.parwar.german_learning.data.models.FlashCard
import com.parwar.german_learning.data.models.StudyMode
import com.parwar.german_learning.data.models.StudySession
import com.parwar.german_learning.data.models.TestMode
import com.parwar.german_learning.utils.TextToSpeechManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuizStats(
    val correctAnswers: Int = 0,
    val totalQuestions: Int = 0,
    val accuracyPercentage: Int = 0
)

data class AnswerRecord(
    val cardId: Long,
    val question: String,
    val correctAnswer: String,
    val userAnswer: String,
    val isCorrect: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val testMode: TestMode
)

@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val flashCardDao: FlashCardDao,
    private val dialogDao: DialogDao,
    private val studySessionDao: StudySessionDao,
    private val textToSpeechManager: TextToSpeechManager
) : ViewModel() {

    private var allCards: List<FlashCard> = emptyList()
    private var allDialogPairs: List<DialogPair> = emptyList()
    private var currentIndex = 0
    private var testedCards = mutableSetOf<Int>()
    private var isRandomMode = false
    private var pendingStartCardNumber: Int? = null

    private val _currentCard = MutableStateFlow<Any?>(null)
    val currentCard: StateFlow<Any?> = _currentCard.asStateFlow()

    private val _showAnswer = MutableStateFlow(false)
    val showAnswer: StateFlow<Boolean> = _showAnswer.asStateFlow()

    private val _quizStats = MutableStateFlow(QuizStats())
    val quizStats: StateFlow<QuizStats> = _quizStats.asStateFlow()

    private val _startingCardNumber = MutableStateFlow(1)
    val startingCardNumber: StateFlow<Int> = _startingCardNumber.asStateFlow()

    private val _testMode = MutableStateFlow(TestMode.WRITING)
    val testMode: StateFlow<TestMode> = _testMode.asStateFlow()

    private val _userInput = MutableStateFlow("")
    val userInput: StateFlow<String> = _userInput.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _sessionAnswers = MutableStateFlow<List<AnswerRecord>>(emptyList())
    private var currentSession: StudySession? = null

    init {
        loadCards()
    }

    private fun loadCards() {
        viewModelScope.launch {
            android.util.Log.d("PracticeViewModel", "Loading cards with test mode: ${_testMode.value}")
            android.util.Log.d("PracticeViewModel", "Current starting card number: ${_startingCardNumber.value}")
            
            when (_testMode.value) {
                TestMode.DIALOG_QUESTION, TestMode.DIALOG_ANSWER -> {
                    dialogDao.getAllDialogs().collect { dialogs ->
                        android.util.Log.d("PracticeViewModel", "Loaded dialogs count: ${dialogs.size}")
                        allDialogPairs = mutableListOf()
                        var currentDialogIndex = 1
                        val dialogPairIndices = mutableMapOf<Int, Int>() // Maps dialog number to pair index
                        
                        dialogs.forEach { dialog ->
                            val startPairIndex = allDialogPairs.size
                            val pairs = dialog.parseDialogPairs()
                            allDialogPairs = allDialogPairs + pairs
                            dialogPairIndices[currentDialogIndex] = startPairIndex
                            currentDialogIndex++
                        }
                        
                        android.util.Log.d("PracticeViewModel", "Total dialog pairs: ${allDialogPairs.size}")
                        android.util.Log.d("PracticeViewModel", "Dialog pair indices map: $dialogPairIndices")
                        
                        // Handle any pending card number selection
                        if (pendingStartCardNumber != null) {
                            android.util.Log.d("PracticeViewModel", "Applying pending card number: $pendingStartCardNumber")
                            val selectedDialogNumber = pendingStartCardNumber!!
                            val pairIndex = dialogPairIndices[selectedDialogNumber] ?: 0
                            android.util.Log.d("PracticeViewModel", "Starting from dialog $selectedDialogNumber, pair index: $pairIndex")
                            
                            if (allDialogPairs.isNotEmpty()) {
                                currentIndex = pairIndex
                                _currentCard.value = allDialogPairs[currentIndex]
                                testedCards.clear()
                                testedCards.add(currentIndex)
                            }
                            pendingStartCardNumber = null
                        } else {
                            if (allDialogPairs.isNotEmpty()) {
                                currentIndex = dialogPairIndices[_startingCardNumber.value] ?: 0
                                android.util.Log.d("PracticeViewModel", "Setting current index to: $currentIndex")
                                _currentCard.value = allDialogPairs[currentIndex]
                                android.util.Log.d("PracticeViewModel", "Current card set to dialog pair at index: $currentIndex")
                                testedCards.clear()
                                testedCards.add(currentIndex)
                            }
                        }
                    }
                }
                else -> {
                    flashCardDao.getAllFlashCards().collect { cards ->
                        allCards = cards
                        if (_startingCardNumber.value > cards.size) {
                            _startingCardNumber.value = cards.size.coerceAtLeast(1)
                        }
                        if (allCards.isNotEmpty()) {
                            _currentCard.value = allCards[currentIndex]
                        }
                    }
                }
            }
        }
    }

    private fun selectNextCard() {
        when (_testMode.value) {
            TestMode.DIALOG_QUESTION, TestMode.DIALOG_ANSWER -> {
                if (allDialogPairs.isEmpty()) return

                if (isRandomMode) {
                    if (testedCards.size < allDialogPairs.size) {
                        val availableIndices = allDialogPairs.indices.filter { it !in testedCards }
                        if (availableIndices.isNotEmpty()) {
                            val nextIndex = availableIndices.random()
                            _currentCard.value = allDialogPairs[nextIndex]
                            testedCards.add(nextIndex)
                            currentIndex = nextIndex
                        } else {
                            finishSession()
                        }
                    } else {
                        finishSession()
                    }
                } else {
                    if (currentIndex < allDialogPairs.size - 1) {
                        currentIndex++
                        _currentCard.value = allDialogPairs[currentIndex]
                        testedCards.add(currentIndex)
                    } else {
                        finishSession()
                    }
                }
            }
            else -> {
                if (allCards.isEmpty()) return

                if (isRandomMode) {
                    if (testedCards.size < allCards.size) {
                        val availableIndices = allCards.indices.filter { it !in testedCards }
                        if (availableIndices.isNotEmpty()) {
                            val nextIndex = availableIndices.random()
                            _currentCard.value = allCards[nextIndex]
                            testedCards.add(nextIndex)
                            currentIndex = nextIndex
                        } else {
                            finishSession()
                        }
                    } else {
                        finishSession()
                    }
                } else {
                    if (currentIndex < allCards.size - 1) {
                        currentIndex++
                        _currentCard.value = allCards[currentIndex]
                        testedCards.add(currentIndex)
                    } else {
                        finishSession()
                    }
                }
            }
        }
    }

    fun startNewSession() {
        viewModelScope.launch {
            _sessionAnswers.value = emptyList()
            currentSession = StudySession(
                mode = StudyMode.QUIZ,
                startTime = System.currentTimeMillis(),
                testMode = _testMode.value
            )
            currentSession?.let { session ->
                val id = studySessionDao.insertSession(session)
                currentSession = session.copy(id = id)
            }
            _quizStats.value = QuizStats()
            testedCards.clear()
            currentIndex = _startingCardNumber.value - 1
            
            when (_testMode.value) {
                TestMode.DIALOG_QUESTION, TestMode.DIALOG_ANSWER -> {
                    if (allDialogPairs.isNotEmpty()) {
                        _currentCard.value = allDialogPairs[currentIndex]
                        testedCards.add(currentIndex)
                    }
                }
                else -> {
                    if (allCards.isNotEmpty()) {
                        _currentCard.value = allCards[currentIndex]
                        testedCards.add(currentIndex)
                    }
                }
            }
        }
    }

    fun setStartingCardNumber(number: Int) {
        android.util.Log.d("PracticeViewModel", "setStartingCardNumber called with number: $number")
        android.util.Log.d("PracticeViewModel", "Current test mode: ${_testMode.value}")
        
        when (_testMode.value) {
            TestMode.DIALOG_QUESTION, TestMode.DIALOG_ANSWER -> {
                if (allDialogPairs.isEmpty()) {
                    android.util.Log.d("PracticeViewModel", "Dialog pairs list is empty, saving as pending number")
                    pendingStartCardNumber = number
                    return
                }
                _startingCardNumber.value = number
            }
            else -> {
                if (allCards.isEmpty()) return
                _startingCardNumber.value = number.coerceIn(1, allCards.size)
                android.util.Log.d("PracticeViewModel", "Dialog - Coerced starting number: ${_startingCardNumber.value}")
                currentIndex = _startingCardNumber.value - 1
                android.util.Log.d("PracticeViewModel", "Dialog - Set current index to: $currentIndex")
                _currentCard.value = allCards[currentIndex]
                android.util.Log.d("PracticeViewModel", "Dialog - Set current card to pair at index: $currentIndex")
                testedCards.clear()
                testedCards.add(currentIndex)
            }
        }
    }

    fun setTestMode(mode: TestMode) {
        _testMode.value = mode
        resetTest()
        loadCards() // Reload cards based on new test mode
    }

    fun setRandomMode(enabled: Boolean) {
        isRandomMode = enabled
        testedCards.clear()
        if (enabled && _currentCard.value != null) {
            testedCards.add(currentIndex)
        }
    }

    fun updateUserInput(input: String) {
        _userInput.value = input
    }

    fun startListening() {
        _isListening.value = true
        _currentCard.value?.let { card ->
            when (card) {
                is FlashCard -> textToSpeechManager.speakGerman(card.germanText)
                is DialogPair -> when (_testMode.value) {
                    TestMode.DIALOG_QUESTION -> textToSpeechManager.speakGerman(card.germanQuestion)
                    TestMode.DIALOG_ANSWER -> textToSpeechManager.speakGerman(card.germanAnswer)
                    else -> {}
                }
                else -> {}
            }
        }
    }

    fun stopListening() {
        _isListening.value = false
        textToSpeechManager.stop()
    }

    fun showAnswer() {
        _showAnswer.value = true
    }

    fun checkAnswer() {
        viewModelScope.launch {
            val currentCard = _currentCard.value ?: return@launch
            val isCorrect = when (_testMode.value) {
                TestMode.WRITING -> checkWritingAnswer()
                TestMode.LISTENING -> checkListeningAnswer()
                TestMode.READING -> true // Reading is self-assessed
                TestMode.DIALOG_QUESTION -> checkDialogQuestionAnswer()
                TestMode.DIALOG_ANSWER -> checkDialogAnswerAnswer()
                else -> false
            }

            // Record the answer
            val answerRecord = when (currentCard) {
                is FlashCard -> AnswerRecord(
                    cardId = currentCard.id,
                    question = currentCard.englishText,
                    correctAnswer = currentCard.germanText,
                    userAnswer = _userInput.value.trim(),
                    isCorrect = isCorrect,
                    testMode = _testMode.value
                )
                is DialogPair -> AnswerRecord(
                    cardId = currentCard.id,
                    question = if (_testMode.value == TestMode.DIALOG_QUESTION) currentCard.germanAnswer else currentCard.germanQuestion,
                    correctAnswer = if (_testMode.value == TestMode.DIALOG_QUESTION) currentCard.germanQuestion else currentCard.germanAnswer,
                    userAnswer = _userInput.value.trim(),
                    isCorrect = isCorrect,
                    testMode = _testMode.value
                )
                else -> null
            }

            if (answerRecord != null) {
                _sessionAnswers.value = _sessionAnswers.value + answerRecord
            }

            // Always move to next card
            submitAnswer(isCorrect)

            // Show brief feedback
            _feedbackMessage.value = if (isCorrect) "Correct!" else "Incorrect"
        }
    }

    private fun normalizeText(text: String): String {
        return text
            .trim() // Remove leading/trailing spaces
            .lowercase() // Convert to lowercase
            .replace(Regex("[.,!?\"']+"), "") // Remove punctuation
            .replace(Regex("\\s+"), " ") // Normalize multiple spaces to single space
    }

    private fun checkWritingAnswer(): Boolean {
        val currentCard = _currentCard.value as? FlashCard ?: return false
        val userAnswer = normalizeText(_userInput.value)
        val correctAnswer = normalizeText(currentCard.germanText)
        return userAnswer == correctAnswer || _userInput.value.trim().equals(currentCard.germanText.trim(), ignoreCase = true)
    }

    private fun checkListeningAnswer(): Boolean {
        val currentCard = _currentCard.value as? FlashCard ?: return false
        if (_userInput.value.isBlank()) {
            return false
        }
        val userAnswer = normalizeText(_userInput.value)
        val correctAnswer = normalizeText(currentCard.germanText)
        return userAnswer == correctAnswer || _userInput.value.trim().equals(currentCard.germanText.trim(), ignoreCase = true)
    }

    private fun checkDialogQuestionAnswer(): Boolean {
        val currentCard = _currentCard.value as? DialogPair ?: return false
        val userAnswer = normalizeText(_userInput.value)
        val correctAnswer = normalizeText(currentCard.germanQuestion)
        return userAnswer == correctAnswer || _userInput.value.trim().equals(currentCard.germanQuestion.trim(), ignoreCase = true)
    }

    private fun checkDialogAnswerAnswer(): Boolean {
        val currentCard = _currentCard.value as? DialogPair ?: return false
        val userAnswer = normalizeText(_userInput.value)
        val correctAnswer = normalizeText(currentCard.germanAnswer)
        return userAnswer == correctAnswer || _userInput.value.trim().equals(currentCard.germanAnswer.trim(), ignoreCase = true)
    }

    fun submitAnswer(correct: Boolean) {
        viewModelScope.launch {
            val currentStats = _quizStats.value
            val newCorrect = currentStats.correctAnswers + if (correct) 1 else 0
            val newTotal = currentStats.totalQuestions + 1
            val newAccuracy = if (newTotal > 0) (newCorrect * 100) / newTotal else 0

            _quizStats.value = QuizStats(
                correctAnswers = newCorrect,
                totalQuestions = newTotal,
                accuracyPercentage = newAccuracy
            )

            // Update card review data
            _currentCard.value?.let { card ->
                when (card) {
                    is FlashCard -> {
                        val updatedCard = card.copy(
                            reviewCount = card.reviewCount + 1,
                            lastReviewed = System.currentTimeMillis(),
                            difficulty = updateDifficulty(card.difficulty, correct)
                        )
                        flashCardDao.updateFlashCard(updatedCard)
                    }
                    is DialogPair -> {
                        // For now, we'll just track the practice stats without updating the dialog
                        // Since DialogPair is part of a Dialog, we don't update it directly
                    }
                }
            }

            // Show next card
            _showAnswer.value = false
            selectNextCard()

            // Reset state for next card
            _userInput.value = ""
            _isListening.value = false
        }
    }

    private fun updateDifficulty(currentDifficulty: Float, wasCorrect: Boolean): Float {
        val adjustment = if (wasCorrect) -0.1f else 0.1f
        return (currentDifficulty + adjustment).coerceIn(0f, 1f)
    }

    fun finishSession() {
        viewModelScope.launch {
            currentSession?.let { session ->
                val wrongAnswers = _sessionAnswers.value.filter { !it.isCorrect }
                val updatedSession = session.copy(
                    endTime = System.currentTimeMillis(),
                    cardsReviewed = _quizStats.value.totalQuestions,
                    correctAnswers = _quizStats.value.correctAnswers,
                    wrongAnswers = wrongAnswers.size,
                    testMode = _testMode.value,
                    wrongAnswerDetails = wrongAnswers.joinToString("\n\n") {
                        "Question: ${it.question}\nCorrect: ${it.correctAnswer}\nYour answer: ${it.userAnswer}"
                    }
                )
                studySessionDao.updateSession(updatedSession)
                currentSession = null
            }
            _currentCard.value = null

            // Show final results dialog
            _feedbackMessage.value = buildResultsSummary()
        }
    }

    private fun buildResultsSummary(): String {
        val wrongAnswers = _sessionAnswers.value.filter { !it.isCorrect }
        return buildString {
            appendLine("Session Complete!")
            appendLine("Total Questions: ${_quizStats.value.totalQuestions}")
            appendLine("Correct Answers: ${_quizStats.value.correctAnswers}")
            appendLine("Accuracy: ${_quizStats.value.accuracyPercentage}%")
            if (wrongAnswers.isNotEmpty()) {
                appendLine("\nIncorrect Answers:")
                wrongAnswers.forEach { answer ->
                    appendLine("\nQuestion: ${answer.question}")
                    appendLine("Correct Answer: ${answer.correctAnswer}")
                    appendLine("Your Answer: ${answer.userAnswer}")
                }
            }
        }
    }

    fun resetTest() {
        _userInput.value = ""
        _isListening.value = false
        _feedbackMessage.value = null
    }
}
