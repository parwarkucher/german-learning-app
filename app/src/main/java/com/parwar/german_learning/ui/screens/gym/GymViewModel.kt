package com.parwar.german_learning.ui.screens.gym

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parwar.german_learning.data.dao.FlashCardDao
import com.parwar.german_learning.data.dao.DialogDao
import com.parwar.german_learning.data.models.FlashCard
import com.parwar.german_learning.data.models.Dialog
import com.parwar.german_learning.data.models.PopupSettings
import com.parwar.german_learning.media.MediaManager
import com.parwar.german_learning.media.MediaControlCallback
import com.parwar.german_learning.notifications.PopupNotificationService
import com.parwar.german_learning.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "GymViewModel"

// Data class to hold question-answer pairs
data class QAPair(
    val germanQuestion: String,
    val englishQuestion: String,
    val germanAnswer: String,
    val englishAnswer: String
)

@HiltViewModel
class GymViewModel @Inject constructor(
    private val flashCardDao: FlashCardDao,
    private val dialogDao: DialogDao,
    private val mediaManager: MediaManager,
    private val preferencesManager: PreferencesManager,
    @ApplicationContext private val context: Context
) : ViewModel(), MediaControlCallback {

    private val _currentCard = MutableStateFlow<FlashCard?>(null)
    val currentCard: StateFlow<FlashCard?> = _currentCard.asStateFlow()

    private val _currentDialog = MutableStateFlow<Dialog?>(null)
    val currentDialog = _currentDialog.asStateFlow()

    private val _currentDialogPair = MutableStateFlow<QAPair?>(null)
    val currentDialogPair = _currentDialogPair.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    val repeatMode = mutableStateOf(false)
    val shuffleMode = mutableStateOf(true)
    val lastPlayedIndex = mutableStateOf(preferencesManager.lastPlayedIndex)
    val speechRate = mutableStateOf(preferencesManager.speechRate)
    val repetitionsPerCard = mutableStateOf(preferencesManager.repetitionsPerCard)
    val dialogMode = mutableStateOf(false)
    val dialogStartIndex = mutableStateOf(0)

    // Popup settings
    private val _popupSettings = MutableStateFlow(preferencesManager.getPopupSettings())
    val popupSettings: StateFlow<PopupSettings> = _popupSettings.asStateFlow()

    private var allCards = emptyList<FlashCard>()
    private var allDialogs = emptyList<Dialog>()
    private var currentIndex = preferencesManager.currentIndex
    private var playbackJob: Job? = null
    private var currentRepetition = 0
    private val cardHistory = mutableListOf<Int>()
    private var historyIndex = -1
    private val playedCards = mutableSetOf<Int>()
    private val playedDialogs = mutableSetOf<Int>()
    private var currentPairIndex = 0
    private var currentDialogPairs = emptyList<QAPair>()

    init {
        Log.d(TAG, "Initializing GymViewModel")
        loadCards()
        loadDialogs()
        viewModelScope.launch {
            mediaManager.startService()
            mediaManager.setSpeechRate(speechRate.value)
            
            // Register as media control callback
            mediaManager.mediaPlaybackService?.setMediaControlCallback(this@GymViewModel)
            Log.d(TAG, "Registered as media control callback")
            
            // Start popup service if enabled
            if (_popupSettings.value.isEnabled) {
                val serviceIntent = Intent(context, PopupNotificationService::class.java).apply {
                    action = "START_POPUP_SERVICE"
                }
                context.startService(serviceIntent)
            }
        }
    }

    private fun loadCards() {
        viewModelScope.launch {
            try {
                flashCardDao.getAllFlashCards().collect { cards ->
                    allCards = cards
                    if (_currentCard.value == null && cards.isNotEmpty()) {
                        currentIndex = preferencesManager.currentIndex.coerceIn(0, cards.size - 1)
                        _currentCard.value = cards[currentIndex]
                        cardHistory.clear()
                        cardHistory.add(currentIndex)
                        historyIndex = 0
                        if (shuffleMode.value) {
                            playedCards.add(currentIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun loadDialogs() {
        viewModelScope.launch {
            try {
                dialogDao.getAllDialogs().collect { dialogs ->
                    allDialogs = dialogs
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updatePopupSettings(settings: PopupSettings) {
        _popupSettings.value = settings
        preferencesManager.savePopupSettings(settings)
        
        val serviceIntent = Intent(context, PopupNotificationService::class.java).apply {
            action = if (settings.isEnabled) "START_POPUP_SERVICE" else "STOP_POPUP_SERVICE"
        }
        context.startService(serviceIntent)
    }

    fun togglePopupEnabled() {
        val currentSettings = _popupSettings.value
        updatePopupSettings(currentSettings.copy(isEnabled = !currentSettings.isEnabled))
    }

    fun setSpeechRate(rate: Float) {
        speechRate.value = rate
        preferencesManager.speechRate = rate
        mediaManager.setSpeechRate(rate)
    }

    fun setRepetitionsPerCard(count: Int) {
        val validCount = count.coerceIn(1, 5)
        repetitionsPerCard.value = validCount
        preferencesManager.repetitionsPerCard = validCount
    }

    fun startFromCard(index: Int) {
        if (index in allCards.indices) {
            currentIndex = index
            _currentCard.value = allCards[index]
            _playbackProgress.value = 0f
            currentRepetition = 0
            preferencesManager.currentIndex = index
            cardHistory.clear()
            cardHistory.add(index)
            historyIndex = 0
            if (shuffleMode.value) {
                playedCards.clear()
                playedCards.add(index)
            }
            if (_isPlaying.value) {
                stopPlayback()
                startPlayback()
            }
        }
    }

    fun resumeFromLast() {
        val savedIndex = preferencesManager.lastPlayedIndex
        if (savedIndex in allCards.indices) {
            startFromCard(savedIndex)
        }
    }

    fun startRandom() {
        if (allCards.isNotEmpty()) {
            if (playedCards.size == allCards.size) {
                playedCards.clear()
            }
            val availableIndices = allCards.indices.filter { it !in playedCards }
            startFromCard(availableIndices.random())
        }
    }

    fun togglePlayback() {
        if (_isPlaying.value) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    fun setDialogMode(enabled: Boolean) {
        dialogMode.value = enabled
        if (enabled) {
            // Switch to dialog mode
            if (allDialogs.isNotEmpty()) {
                currentIndex = dialogStartIndex.value.coerceIn(0, allDialogs.size - 1)
                _currentDialog.value = allDialogs[currentIndex]
                val pairs = _currentDialog.value?.parseDialogPairs() ?: emptyList()
                currentDialogPairs = pairs.map { pair ->
                    QAPair(
                        germanQuestion = pair.germanQuestion,
                        englishQuestion = pair.englishQuestion,
                        germanAnswer = pair.germanAnswer,
                        englishAnswer = pair.englishAnswer
                    )
                }
                currentPairIndex = 0
                _currentDialogPair.value = currentDialogPairs.firstOrNull()
                cardHistory.clear()
                cardHistory.add(currentIndex)
                historyIndex = 0
                playedDialogs.clear()
                if (shuffleMode.value) {
                    playedDialogs.add(currentIndex)
                }
            }
        } else {
            // Switch back to card mode
            if (allCards.isNotEmpty()) {
                currentIndex = preferencesManager.currentIndex.coerceIn(0, allCards.size - 1)
                _currentCard.value = allCards[currentIndex]
                cardHistory.clear()
                cardHistory.add(currentIndex)
                historyIndex = 0
                playedCards.clear()
                if (shuffleMode.value) {
                    playedCards.add(currentIndex)
                }
            }
        }
        if (_isPlaying.value) {
            stopPlayback()
            startPlayback()
        }
    }

    fun setDialogStartIndex(index: Int) {
        dialogStartIndex.value = index.coerceIn(0, allDialogs.size - 1)
        if (dialogMode.value && _currentDialog.value != null) {
            setDialogMode(true) // Restart with new index
        }
    }

    private fun startPlayback() {
        if ((dialogMode.value && allDialogs.isEmpty()) || (!dialogMode.value && allCards.isEmpty())) return

        _isPlaying.value = true
        playbackJob = viewModelScope.launch {
            try {
                while (_isPlaying.value) {
                    if (dialogMode.value) {
                        _currentDialog.value?.let { dialog ->
                            Log.d(TAG, "Starting dialog playback: ${dialog.id}")
                            val pairs = dialog.parseDialogPairs()
                            Log.d(TAG, "Found ${pairs.size} Q&A pairs")
                            
                            // Start from current pair index
                            for (index in currentPairIndex until pairs.size) {
                                val pair = pairs[index]
                                _currentDialogPair.value = QAPair(
                                    germanQuestion = pair.germanQuestion,
                                    englishQuestion = pair.englishQuestion,
                                    germanAnswer = pair.germanAnswer,
                                    englishAnswer = pair.englishAnswer
                                )
                                currentPairIndex = index
                                Log.d(TAG, "Playing pair ${index + 1}/${pairs.size}:")
                                Log.d(TAG, "German Q: ${pair.germanQuestion}")
                                Log.d(TAG, "German A: ${pair.germanAnswer}")
                                Log.d(TAG, "English Q: ${pair.englishQuestion}")
                                Log.d(TAG, "English A: ${pair.englishAnswer}")
                                
                                repeat(repetitionsPerCard.value) { repetition ->
                                    currentRepetition = repetition + 1
                                    Log.d(TAG, "Repetition ${repetition + 1}/${repetitionsPerCard.value}")

                                    // Play question in both languages
                                    Log.d(TAG, "Speaking German/English Question sequence")
                                    mediaManager.speakSequence(pair.germanQuestion.trim(), pair.englishQuestion.trim())
                                    val questionDelay = ((pair.germanQuestion.length + pair.englishQuestion.length) * 100).coerceAtLeast(4000)
                                    delay(questionDelay.toLong())
                                    
                                    // Play answer in both languages
                                    Log.d(TAG, "Speaking German/English Answer sequence")
                                    mediaManager.speakSequence(pair.germanAnswer.trim(), pair.englishAnswer.trim())
                                    val answerDelay = ((pair.germanAnswer.length + pair.englishAnswer.length) * 100).coerceAtLeast(4000)
                                    delay(answerDelay.toLong())
                                }
                            }

                            // After finishing all pairs in the current dialog
                            if (!repeatMode.value) {
                                currentRepetition = 0
                                currentPairIndex = 0  // Reset pair index
                                
                                // Move to next dialog
                                if (shuffleMode.value) {
                                    if (playedDialogs.size == allDialogs.size) {
                                        playedDialogs.clear()
                                    }
                                    val availableIndices = allDialogs.indices.filter { it !in playedDialogs }
                                    currentIndex = availableIndices.random()
                                    playedDialogs.add(currentIndex)
                                } else {
                                    currentIndex = (currentIndex + 1) % allDialogs.size
                                }

                                // Update history
                                if (historyIndex < cardHistory.size - 1) {
                                    cardHistory.subList(historyIndex + 1, cardHistory.size).clear()
                                }
                                cardHistory.add(currentIndex)
                                historyIndex = cardHistory.size - 1

                                // Load new dialog
                                _currentDialog.value = allDialogs[currentIndex]
                                val newPairs = _currentDialog.value?.parseDialogPairs() ?: emptyList()
                                currentDialogPairs = newPairs.map { pair ->
                                    QAPair(
                                        germanQuestion = pair.germanQuestion,
                                        englishQuestion = pair.englishQuestion,
                                        germanAnswer = pair.germanAnswer,
                                        englishAnswer = pair.englishAnswer
                                    )
                                }
                                _currentDialogPair.value = currentDialogPairs.firstOrNull()
                            }
                        }
                    } else {
                        while (_isPlaying.value) {  // Continuous playback loop
                            _currentCard.value?.let { card ->
                                repeat(repetitionsPerCard.value) { repetition ->
                                    currentRepetition = repetition + 1

                                    // Play German and English text in sequence
                                    Log.d(TAG, "Speaking German/English sequence for flashcard")
                                    mediaManager.speakSequence(card.germanText, card.englishText)
                                    val sequenceDelay = ((card.germanText.length + card.englishText.length) * 100).coerceAtLeast(4000)
                                    delay(sequenceDelay.toLong())

                                    card.examples.firstOrNull()?.let { example ->
                                        delay(500)
                                        mediaManager.speakGerman(example)
                                        val exampleDelay = (example.length * 100).coerceAtLeast(2500)
                                        delay(exampleDelay.toLong())
                                    }
                                }

                                // Only show progress indicator for card mode
                                for (progress in 0..100) {
                                    _playbackProgress.value = progress / 100f
                                    delay(20)
                                }

                                if (!shuffleMode.value) {
                                    lastPlayedIndex.value = currentIndex
                                    preferencesManager.lastPlayedIndex = currentIndex
                                }

                                if (!repeatMode.value) {
                                    // Move to next card within the same coroutine
                                    currentRepetition = 0
                                    currentIndex = (currentIndex + 1) % allCards.size
                                    
                                    if (shuffleMode.value) {
                                        if (playedCards.size == allCards.size) {
                                            playedCards.clear()
                                        }
                                        val availableIndices = allCards.indices.filter { it !in playedCards }
                                        currentIndex = availableIndices.random()
                                        playedCards.add(currentIndex)
                                    }

                                    if (historyIndex < cardHistory.size - 1) {
                                        cardHistory.subList(historyIndex + 1, cardHistory.size).clear()
                                    }
                                    cardHistory.add(currentIndex)
                                    historyIndex = cardHistory.size - 1

                                    _currentCard.value = allCards[currentIndex]
                                    _playbackProgress.value = 0f
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                stopPlayback()
            }
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        mediaManager.stop()
        _isPlaying.value = false
        _playbackProgress.value = 0f
        viewModelScope.launch {
            try {
                // Reinitialize TTS state to ensure clean state for next playback
                mediaManager.reinitialize()
            } catch (e: Exception) {
                Log.e(TAG, "Error reinitializing TTS", e)
            }
        }
    }

    fun skipToNext() {
        if (dialogMode.value) {
            if (allDialogs.isEmpty()) return

            try {
                Log.d(TAG, "skipToNext: Starting navigation in dialog mode")
                currentRepetition = 0
                
                // Stop any ongoing playback sequence
                playbackJob?.cancel()
                mediaManager.stop()
                
                // If we have pairs loaded, try to move to next pair first
                if (currentPairIndex < currentDialogPairs.size - 1) {
                    Log.d(TAG, "skipToNext: Moving to next pair ${currentPairIndex + 2}/${currentDialogPairs.size} within dialog")
                    // Move to next pair within same dialog
                    currentPairIndex++
                    _currentDialogPair.value = currentDialogPairs[currentPairIndex]
                    _playbackProgress.value = 0f
                    
                    // Always start playback when moving to next pair
                    Log.d(TAG, "skipToNext: Starting new playback for pair ${currentPairIndex + 1}/${currentDialogPairs.size}")
                    playbackJob?.cancel()
                    mediaManager.stop()
                    _isPlaying.value = true  // Ensure playing state is set
                    startPlayback()
                    return
                }

                Log.d(TAG, "skipToNext: At last pair, moving to next dialog")
                // If we're at the last pair or no pairs, move to next dialog
                if (shuffleMode.value) {
                    if (playedDialogs.size == allDialogs.size) {
                        Log.d(TAG, "skipToNext: All dialogs played, resetting played list")
                        playedDialogs.clear()
                    }
                    val availableIndices = allDialogs.indices.filter { it !in playedDialogs }
                    currentIndex = availableIndices.random()
                    playedDialogs.add(currentIndex)
                    Log.d(TAG, "skipToNext: Randomly selected next dialog at index: $currentIndex")
                } else {
                    currentIndex = (currentIndex + 1) % allDialogs.size
                    Log.d(TAG, "skipToNext: Moving to next sequential dialog at index: $currentIndex")
                }
                
                if (historyIndex < cardHistory.size - 1) {
                    cardHistory.subList(historyIndex + 1, cardHistory.size).clear()
                }
                cardHistory.add(currentIndex)
                historyIndex = cardHistory.size - 1

                Log.d(TAG, "skipToNext: Loading new dialog at index: $currentIndex")
                _currentDialog.value = allDialogs[currentIndex]
                val pairs = _currentDialog.value?.parseDialogPairs() ?: emptyList()
                currentDialogPairs = pairs.map { pair ->
                    QAPair(
                        germanQuestion = pair.germanQuestion,
                        englishQuestion = pair.englishQuestion,
                        germanAnswer = pair.germanAnswer,
                        englishAnswer = pair.englishAnswer
                    )
                }
                currentPairIndex = 0
                _currentDialogPair.value = currentDialogPairs.firstOrNull()
                _playbackProgress.value = 0f
                
                // Always start playback when moving to next dialog
                Log.d(TAG, "skipToNext: Starting new playback for first pair of new dialog")
                playbackJob?.cancel()
                mediaManager.stop()
                _isPlaying.value = true  // Ensure playing state is set
                startPlayback()
            } catch (e: Exception) {
                Log.e(TAG, "skipToNext: Error during navigation", e)
            }
        } else {
            if (allCards.isEmpty()) return

            try {
                Log.d(TAG, "skipToNext for card mode: Starting navigation")
                currentRepetition = 0
                
                if (historyIndex < cardHistory.size - 1) {
                    historyIndex++
                    currentIndex = cardHistory[historyIndex]
                } else {
                    if (shuffleMode.value) {
                        if (playedCards.size == allCards.size) {
                            playedCards.clear()
                        }
                        val availableIndices = allCards.indices.filter { it !in playedCards }
                        currentIndex = availableIndices.random()
                        playedCards.add(currentIndex)
                    } else {
                        currentIndex = (currentIndex + 1) % allCards.size
                    }

                    if (historyIndex < cardHistory.size - 1) {
                        cardHistory.subList(historyIndex + 1, cardHistory.size).clear()
                    }
                    cardHistory.add(currentIndex)
                    historyIndex = cardHistory.size - 1
                }

                if (!shuffleMode.value) {
                    lastPlayedIndex.value = currentIndex
                    preferencesManager.lastPlayedIndex = currentIndex
                }

                _currentCard.value = allCards[currentIndex]
                _playbackProgress.value = 0f
                
                // First stop current playback
                _isPlaying.value = false
                playbackJob?.cancel()
                mediaManager.stop()
                
                // Then reinitialize TTS to ensure a clean state
                viewModelScope.launch {
                    try {
                        Log.d(TAG, "skipToNext: Reinitializing TTS before starting new playback")
                        mediaManager.reinitialize()
                        
                        // Then start new playback
                        Log.d(TAG, "skipToNext: Starting new playback for card ${currentIndex + 1}/${allCards.size}")
                        _isPlaying.value = true
                        startPlayback()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reinitializing TTS in skipToNext", e)
                    }
                }
            } catch (e: Exception) {
                // Handle error
                Log.e(TAG, "Error in skipToNext for card mode", e)
            }
        }
    }

    fun skipToPrevious() {
        if (dialogMode.value) {
            if (allDialogs.isEmpty()) return

            try {
                Log.d(TAG, "skipToPrevious: Starting navigation in dialog mode")
                currentRepetition = 0
                
                // Stop any ongoing playback sequence
                playbackJob?.cancel()
                mediaManager.stop()
                
                // Try to move to previous pair first
                if (currentPairIndex > 0) {
                    Log.d(TAG, "skipToPrevious: Moving to previous pair ${currentPairIndex}/${currentDialogPairs.size} within dialog")
                    // Move to previous pair within same dialog
                    currentPairIndex--
                    _currentDialogPair.value = currentDialogPairs[currentPairIndex]
                    _playbackProgress.value = 0f
                    
                    // Always start playback when moving to previous pair
                    Log.d(TAG, "skipToPrevious: Starting new playback for pair ${currentPairIndex + 1}/${currentDialogPairs.size}")
                    playbackJob?.cancel()
                    mediaManager.stop()
                    _isPlaying.value = true  // Ensure playing state is set
                    startPlayback()
                    return
                }

                Log.d(TAG, "skipToPrevious: At first pair, moving to previous dialog")
                // If we're at the first pair, move to previous dialog
                if (historyIndex > 0) {
                    Log.d(TAG, "skipToPrevious: Moving to previous dialog")
                    historyIndex--
                    currentIndex = cardHistory[historyIndex]
                    
                    Log.d(TAG, "skipToPrevious: Loading previous dialog at index: $currentIndex")
                    _currentDialog.value = allDialogs[currentIndex]
                    val pairs = _currentDialog.value?.parseDialogPairs() ?: emptyList()
                    currentDialogPairs = pairs.map { pair ->
                        QAPair(
                            germanQuestion = pair.germanQuestion,
                            englishQuestion = pair.englishQuestion,
                            germanAnswer = pair.germanAnswer,
                            englishAnswer = pair.englishAnswer
                        )
                    }
                    currentPairIndex = currentDialogPairs.size - 1
                    _currentDialogPair.value = currentDialogPairs.lastOrNull()
                    _playbackProgress.value = 0f
                    
                    // Always start playback when moving to previous dialog
                    Log.d(TAG, "skipToPrevious: Starting new playback for last pair of previous dialog")
                    playbackJob?.cancel()
                    mediaManager.stop()
                    _isPlaying.value = true  // Ensure playing state is set
                    startPlayback()
                } else {
                    Log.d(TAG, "skipToPrevious: Already at first dialog, cannot go back further")
                }
            } catch (e: Exception) {
                Log.e(TAG, "skipToPrevious: Error during navigation", e)
            }
        } else {
            if (allCards.isEmpty() || historyIndex <= 0) return

            try {
                Log.d(TAG, "skipToPrevious for card mode: Starting navigation")
                currentRepetition = 0
                historyIndex--
                currentIndex = cardHistory[historyIndex]
                
                if (!shuffleMode.value) {
                    lastPlayedIndex.value = currentIndex
                    preferencesManager.lastPlayedIndex = currentIndex
                }

                _currentCard.value = allCards[currentIndex]
                _playbackProgress.value = 0f
                
                // First stop current playback
                _isPlaying.value = false
                playbackJob?.cancel()
                mediaManager.stop()
                
                // Then reinitialize TTS to ensure a clean state
                viewModelScope.launch {
                    try {
                        Log.d(TAG, "skipToPrevious: Reinitializing TTS before starting new playback")
                        mediaManager.reinitialize()
                        
                        // Then start new playback
                        Log.d(TAG, "skipToPrevious: Starting new playback for card ${currentIndex + 1}/${allCards.size}")
                        _isPlaying.value = true
                        startPlayback()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reinitializing TTS in skipToPrevious", e)
                    }
                }
            } catch (e: Exception) {
                // Handle error
                Log.e(TAG, "Error in skipToPrevious for card mode", e)
            }
        }
    }

    fun setRepeatMode(enabled: Boolean) {
        repeatMode.value = enabled
    }

    fun setShuffleMode(enabled: Boolean) {
        shuffleMode.value = enabled
        if (enabled) {
            cardHistory.clear()
            cardHistory.add(currentIndex)
            historyIndex = 0
            if (dialogMode.value) {
                playedDialogs.clear()
                playedDialogs.add(currentIndex)
            } else {
                playedCards.clear()
                playedCards.add(currentIndex)
            }
        }
    }

    override fun onPlayPause() {
        Log.d(TAG, "onPlayPause called, current playing state: ${_isPlaying.value}")
        togglePlayback()
    }

    override fun onNext() {
        Log.d(TAG, "onNext called")
        skipToNext()
    }

    override fun onPrevious() {
        Log.d(TAG, "onPrevious called")
        skipToPrevious()
    }

    override fun onStop() {
        Log.d(TAG, "onStop called")
        stopPlayback()
    }

    override fun isPlaying(): Boolean {
        Log.d(TAG, "isPlaying called, returning: ${_isPlaying.value}")
        return _isPlaying.value
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
        mediaManager.stopService()
    }
}
