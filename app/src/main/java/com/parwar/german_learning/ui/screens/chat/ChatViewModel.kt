package com.parwar.german_learning.ui.screens.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parwar.german_learning.data.dao.DialogDao
import com.parwar.german_learning.data.dao.FlashCardDao
import com.parwar.german_learning.data.dao.SavedChatDao
import com.parwar.german_learning.data.models.*
import com.parwar.german_learning.media.MediaManager
import com.parwar.german_learning.network.Message
import com.parwar.german_learning.network.ModelInfo
import com.parwar.german_learning.network.OpenRouterService
import com.parwar.german_learning.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.speech.tts.Voice

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val openRouterService: OpenRouterService,
    private val flashCardDao: FlashCardDao,
    private val savedChatDao: SavedChatDao,
    private val dialogDao: DialogDao,
    private val preferencesManager: PreferencesManager,
    private val mediaManager: MediaManager
) : ViewModel() {

    private val TAG = "ChatViewModel"

    private val _chatState = MutableStateFlow(ChatState())
    val chatState = _chatState.asStateFlow()

    private val _tokenInfo = MutableStateFlow(Pair(0.0, 0.0)) // total cost, last message cost
    val tokenInfo = _tokenInfo.asStateFlow()

    private var totalConversationTokens: Int = 0
        set(value) {
            field = value
            val model = _chatState.value.config.selectedModel
            val cost = (value / 1_000_000.0) * (model.pricing.inputPrice + model.pricing.outputPrice)
            _tokenInfo.value = _tokenInfo.value.copy(first = cost)
            Log.d(TAG, "Total conversation tokens: $value, Cost: $$cost")
        }

    private var lastMessageTokens: Int = 0
        set(value) {
            field = value
            val model = _chatState.value.config.selectedModel
            val cost = (value / 1_000_000.0) * (model.pricing.inputPrice + model.pricing.outputPrice)
            _tokenInfo.value = _tokenInfo.value.copy(second = cost)
            Log.d(TAG, "Last message tokens: $value, Cost: $$cost")
        }

    companion object {
        private const val CONTEXT_THRESHOLD_PERCENTAGE = 0.8 // Remove messages when reaching 80% of limit
        private const val MESSAGES_TO_KEEP_PERCENTAGE = 0.7 // Keep 70% of most recent messages
    }

    private val _availableModels = MutableStateFlow(OpenRouterService.AVAILABLE_MODELS)
    val availableModels = _availableModels.asStateFlow()

    private val _savedChats = MutableStateFlow<List<SavedChat>>(emptyList())
    val savedChats = _savedChats.asStateFlow()

    private var _showSaveChatDialog = MutableStateFlow(false)
    val showSaveChatDialog = _showSaveChatDialog.asStateFlow()

    private val _autoReadEnabled = MutableStateFlow(false)
    val autoReadEnabled = _autoReadEnabled.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate = _speechRate.asStateFlow()

    private var isReading = false

    // Voice management
    val availableGermanVoices = mediaManager.availableGermanVoices
    val availableEnglishVoices = mediaManager.availableEnglishVoices
    
    private val _selectedVoiceIndex = MutableStateFlow(0)
    val selectedVoiceIndex: StateFlow<Int> = _selectedVoiceIndex.asStateFlow()
    
    private val _currentPitch = MutableStateFlow(1.0f)
    val currentPitch: StateFlow<Float> = _currentPitch.asStateFlow()
    
    fun setPitch(pitch: Float) {
        _currentPitch.value = pitch
        mediaManager.setPitch(pitch)
    }
    
    fun setGermanVoice(index: Int) {
        val voices = availableGermanVoices.value
        if (voices.isNotEmpty() && index in voices.indices) {
            _selectedVoiceIndex.value = index
            mediaManager.setGermanVoice(voices[index])
        }
    }
    
    fun setEnglishVoice(index: Int) {
        val voices = availableEnglishVoices.value
        if (voices.isNotEmpty() && index in voices.indices) {
            mediaManager.setEnglishVoice(voices[index])
        }
    }

    init {
        viewModelScope.launch {
            // Load saved API key and model selection
            val apiKey = preferencesManager.getOpenRouterApiKey()
            val selectedModelId = preferencesManager.getSelectedModel()
            val selectedModel = OpenRouterService.AVAILABLE_MODELS.find { it.id == selectedModelId }
            updateConfig(apiKey, selectedModel)

            // Load saved chats
            savedChatDao.getAllSavedChats().collect { chats ->
                _savedChats.value = chats
            }
        }

        viewModelScope.launch {
            mediaManager.initialize()
        }
    }

    fun updateConfig(apiKey: String? = null, model: ModelInfo? = null, temperature: Float? = null) {
        val currentConfig = _chatState.value.config
        _chatState.value = _chatState.value.copy(
            config = currentConfig.copy(
                apiKey = apiKey ?: currentConfig.apiKey,
                selectedModel = model ?: currentConfig.selectedModel,
                temperature = temperature ?: currentConfig.temperature
            )
        )
        
        // Save to preferences
        viewModelScope.launch {
            apiKey?.let { preferencesManager.saveOpenRouterApiKey(it) }
            model?.let { preferencesManager.saveSelectedModel(it.id) }
        }
    }

    fun setChatMode(mode: ChatMode) {
        _chatState.value = _chatState.value.copy(mode = mode)
        // Clear messages when switching modes
        clearMessages()
    }

    fun setCardRange(startIndex: Int, endIndex: Int, enabled: Boolean) {
        _chatState.value = _chatState.value.copy(
            cardRange = CardRange(
                startIndex = startIndex,
                endIndex = endIndex,
                enabled = enabled
            )
        )
        Log.d(TAG, "Card range updated: start=$startIndex, end=$endIndex, enabled=$enabled")
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        viewModelScope.launch {
            try {
                Log.d(TAG, "Sending message: $content")
                // Add user message
                val userMessage = ChatMessage(content = content, isUser = true)
                addMessage(userMessage)
                _chatState.value = _chatState.value.copy(isLoading = true, error = null)

                // Prepare context and messages
                val messages = mutableListOf<Message>()
                
                if (_chatState.value.mode == ChatMode.CARDS_BASED || _chatState.value.mode == ChatMode.CARDS_AND_DIALOG_BASED) {
                    // Build context for AI
                    val contextBuilder = StringBuilder()
                    
                    // Add vocabulary context
                    contextBuilder.append("=== GERMAN VOCABULARY ===\n")
                    val allCards = flashCardDao.getAllFlashCards().first()
                    Log.d(TAG, "Total flashcards in database: ${allCards.size}")

                    // Apply card range if enabled
                    val cards = if (_chatState.value.cardRange.enabled) {
                        val start = _chatState.value.cardRange.startIndex
                        val end = _chatState.value.cardRange.endIndex.coerceAtMost(allCards.size)
                        Log.d(TAG, "Using card range: $start to $end")
                        allCards.subList(start, end)
                    } else {
                        Log.d(TAG, "Using all cards")
                        allCards
                    }
                    Log.d(TAG, "Cards being used in context: ${cards.size}")
                    
                    // Group cards by category for better organization
                    val groupedCards = cards.groupBy { it.category ?: "Uncategorized" }
                    Log.d(TAG, "=== CARDS BEING SENT TO AI ===")
                    groupedCards.forEach { (category, cardsInCategory) ->
                        Log.d(TAG, "Category: $category")
                        cardsInCategory.forEach { card ->
                            Log.d(TAG, "Card: ${card.germanText} - ${card.examples.joinToString(", ")}")
                        }
                    }
                    Log.d(TAG, "=== END OF CARDS ===")

                    groupedCards.forEach { (category, cardsInCategory) ->
                        contextBuilder.append("\n## $category\n")
                        cardsInCategory.forEach { card ->
                            contextBuilder.append("${card.germanText}")
                            if (card.examples.isNotEmpty()) {
                                contextBuilder.append(" - ${card.examples.joinToString(", ")}")
                            }
                            contextBuilder.append("\n")
                        }
                    }
                    contextBuilder.append("\n=== END OF VOCABULARY (${cards.size} words) ===\n\n")

                    // Add dialog context if it's CARDS_AND_DIALOG_BASED mode
                    if (_chatState.value.mode == ChatMode.CARDS_AND_DIALOG_BASED) {
                        contextBuilder.append("\n=== GERMAN DIALOGS ===\n")
                        val allDialogs = dialogDao.getAllDialogs().first()
                        Log.d(TAG, "Total dialogs in database for combined mode: ${allDialogs.size}")
                        
                        // Group dialogs by category
                        val groupedDialogs = allDialogs.groupBy { it.category.ifBlank { "Uncategorized" } }
                        
                        // For logging - show both German and English
                        Log.d(TAG, "=== DIALOGS BEING SENT TO AI (COMBINED MODE) ===")
                        groupedDialogs.forEach { (category, dialogsInCategory) ->
                            Log.d(TAG, "Category: $category")
                            dialogsInCategory.forEach { dialog ->
                                Log.d(TAG, """Dialog:
                                    |German:
                                    |${dialog.germanText}
                                    |English (not sent to AI):
                                    |${dialog.englishText}
                                    |""".trimMargin())
                            }
                        }
                        Log.d(TAG, "=== END OF DIALOGS ===")
                        
                        // For AI context - only German text
                        groupedDialogs.forEach { (category, dialogsInCategory) ->
                            contextBuilder.append("\n## $category\n")
                            dialogsInCategory.forEach { dialog ->
                                // Only append German text for context
                                contextBuilder.append("${dialog.germanText.trim()}\n\n")
                            }
                        }
                        contextBuilder.append("\n=== END OF DIALOGS ===\n\n")
                    }

                    // Add system message with context (without conversation history)
                    val systemMessage = Message(
                        role = "system",
                        content = """You are a German language teacher. Base your responses STRICTLY on the following vocabulary:

                            |$contextBuilder
                            |
                            |Instructions:
                            |1. Keep responses VERY concise - give only ONE response at a time, only when there is better answer give additional one of that also.
                            |2. ONLY use words from the vocabulary list above - no exceptions (this includes follow-up questions)
                            |3. Mark ONLY German words that are NOT in the vocabulary list with *asterisks*. Do not mark words that are in the list.
                            |4. Format your response in exactly this order:
                            |   a. If there's a correction needed:
                            |      Correction: "correct_word" (you wrote "wrong_word")
                            |   b. ONE German sentence with its translation:
                            |      German: "sentence with *new_word* but no asterisks for vocabulary words"
                            |      English: Translation
                            |   c. If needed, ONE brief note about the sentence (max 2 line)
                            |   d. ONE follow-up question (using ONLY words from vocabulary list):
                            |      German: "question"
                            |      English: Translation
                            |5. If you can't form a response or follow-up question using the vocabulary list, say so briefly and try a simpler question from the list
                            |6. Never give multiple examples or explanations
                        """.trimMargin()
                    )
                    messages.add(systemMessage)
                } else if (_chatState.value.mode == ChatMode.DIALOG_BASED) {
                    // Build context for AI using dialogs
                    val contextBuilder = StringBuilder()
                    
                    // Add dialog context
                    contextBuilder.append("=== GERMAN DIALOGS ===\n")
                    val allDialogs = dialogDao.getAllDialogs().first()
                    Log.d(TAG, "Total dialogs in database: ${allDialogs.size}")

                    // Group dialogs by category
                    val groupedDialogs = allDialogs.groupBy { it.category.ifBlank { "Uncategorized" } }
                    
                    // For logging - show both German and English
                    Log.d(TAG, "=== DIALOGS BEING SENT TO AI ===")
                    groupedDialogs.forEach { (category, dialogsInCategory) ->
                        Log.d(TAG, "Category: $category")
                        dialogsInCategory.forEach { dialog ->
                            Log.d(TAG, """Dialog:
                                |German:
                                |${dialog.germanText}
                                |English (not sent to AI):
                                |${dialog.englishText}
                                |""".trimMargin())
                        }
                    }
                    Log.d(TAG, "=== END OF DIALOGS ===")
                    
                    // For AI context - only German text
                    groupedDialogs.forEach { (category, dialogsInCategory) ->
                        contextBuilder.append("\n## $category\n")
                        dialogsInCategory.forEach { dialog ->
                            // Only append German text for context
                            contextBuilder.append("${dialog.germanText.trim()}\n\n")
                        }
                    }
                    contextBuilder.append("\n=== END OF DIALOGS ===\n\n")

                    // Add system message with context
                    val systemMessage = Message(
                        role = "system",
                        content = """You are a German conversation partner. Base your responses STRICTLY on the following dialogs:

                            |$contextBuilder
                            |
                            |Instructions:
                            |1. Keep responses VERY concise and natural - simulate a real German conversation
                            |2. ONLY use dialog patterns from the examples above
                            |3. Always provide translations in English
                            |4. Format your response in exactly this order:
                            |   a. If there's a correction needed:
                            |      Correction: "correct_word" (you wrote "wrong_word")
                            |   b. Your response in German and English:
                            |      German: Your German response
                            |      English: English translation
                            |   c. Follow-up question based on the dialog patterns:
                            |      German: Your follow-up question
                            |      English: English translation
                            |   d. If needed, ONE brief note about usage or context:
                            |      Note: Brief explanation (max 2 lines)
                            |5. If you can't form a response using the dialog patterns, say so briefly in both German and English
                            |6. Keep the conversation flowing naturally using the dialog examples
                            |7. Mark any German words or phrases that need correction with *asterisks*
                        """.trimMargin()
                    )
                    messages.add(systemMessage)
                } else if (_chatState.value.mode == ChatMode.STORY_CHAT) {
                    // Build context for AI using cards and dialogs
                    val contextBuilder = StringBuilder()
                    
                    // Add vocabulary context
                    contextBuilder.append("=== GERMAN VOCABULARY ===\n")
                    val allCards = flashCardDao.getAllFlashCards().first()
                    Log.d(TAG, "Total flashcards in database: ${allCards.size}")

                    // Apply card range if enabled
                    val cards = if (_chatState.value.cardRange.enabled) {
                        val start = _chatState.value.cardRange.startIndex
                        val end = _chatState.value.cardRange.endIndex.coerceAtMost(allCards.size)
                        Log.d(TAG, "Using card range: $start to $end")
                        allCards.subList(start, end)
                    } else {
                        Log.d(TAG, "Using all cards")
                        allCards
                    }
                    
                    // Group cards by category for better organization
                    val groupedCards = cards.groupBy { it.category ?: "Uncategorized" }
                    groupedCards.forEach { (category, cardsInCategory) ->
                        contextBuilder.append("\n## $category\n")
                        cardsInCategory.forEach { card ->
                            contextBuilder.append("${card.germanText}")
                            if (card.examples.isNotEmpty()) {
                                contextBuilder.append(" - ${card.examples.joinToString(", ")}")
                            }
                            contextBuilder.append("\n")
                        }
                    }
                    contextBuilder.append("\n=== END OF VOCABULARY (${cards.size} words) ===\n\n")

                    // Add dialog context
                    contextBuilder.append("\n=== GERMAN DIALOGS ===\n")
                    val allDialogs = dialogDao.getAllDialogs().first()
                    val groupedDialogs = allDialogs.groupBy { it.category.ifBlank { "Uncategorized" } }
                    
                    groupedDialogs.forEach { (category, dialogsInCategory) ->
                        contextBuilder.append("\n## $category\n")
                        dialogsInCategory.forEach { dialog ->
                            contextBuilder.append("${dialog.germanText.trim()}\n\n")
                        }
                    }
                    contextBuilder.append("\n=== END OF DIALOGS ===\n\n")

                    // Add system message with context
                    val systemMessage = Message(
                        role = "system",
                        content = """You are a German storyteller. Create stories using ALL of the following vocabulary:

                            |$contextBuilder
                            |
                            |Instructions:
                            |1. CRITICAL: You MUST use ALL words from the vocabulary list above in your story
                            |2. CRITICAL: Try to use ONLY words from the vocabulary list strictly
                            |3. Only use additional words if absolutely necessary ,dont always use new words not in list  (like basic connecting words)
                            |4. Keep additional words to an absolute minimum
                            |5. For dialogs in the list, you can be selective and use only relevant ones
                            |6. Format your response in exactly this order:
                            |
                            |📖 Story Title
                            |-------------------------
                            |🇩🇪 ${"\u0020"}${"\u0020"}${"\u0020"}Your title in German (using only vocabulary words)
                            |🇬🇧 ${"\u0020"}${"\u0020"}${"\u0020"}Your title in English
                            |
                            |📝 Story
                            |-------------------------
                            |Format your story with each German paragraph immediately followed by its English translation:
                            |
                            |🇩🇪 [First paragraph in German]
                            |🇬🇧 [English translation of first paragraph]
                            |
                            |🇩🇪 [Second paragraph in German]
                            |🇬🇧 [English translation of second paragraph]
                            |
                            |And so on for each paragraph.
                            |Include ALL words from the vocabulary list.
                            |Keep additional words to a minimum.
                            |
                            |📋 Vocabulary Check
                            |-------------------------
                            |Missing words (if any): List any vocabulary words you couldn't naturally include
                            |Additional words used: List all words used that were not in the vocabulary list
                            |
                            |❓ Question
                            |-------------------------
                            |🇩🇪 ${"\u0020"}${"\u0020"}${"\u0020"}Your question in German (using only vocabulary words)
                            |🇬🇧 ${"\u0020"}${"\u0020"}${"\u0020"}Your question in English
                            |
                            |Guidelines:
                            |1. The story should be as long as needed to use ALL vocabulary naturally
                            |2. Create a coherent narrative that flows well despite needing to use all words
                            |3. Structure the story with a clear beginning, middle, and end
                            |4. Use proper paragraph breaks to make the story more readable
                            |5. Include dialog when it helps use vocabulary words naturally
                            |6. Before sending your response, verify that:
                            |   - EVERY word from the vocabulary list is used at least once
                            |   - You've used as FEW additional words as possible
                            |   - List ALL additional words in the Vocabulary Check section
                            |   - The story remains coherent and engaging despite using all words
                        """.trimMargin()
                    )
                    messages.add(systemMessage)
                } else {
                    // General chat mode - no flashcards or dialogs
                    val systemMessage = Message(
                        role = "system",
                        content = """You are a friendly German language teacher. 
                            |
                            |Instructions:
                            |1. Keep responses VERY concise - give only ONE response at a time
                            |2. Mark ONLY new or unfamiliar German words with *asterisks*. Common words should not be marked.
                            |3. Format your response in exactly this order:
                            |   a. If there's a correction needed:
                            |      Correction: "correct_word" (you wrote "wrong_word")
                            |   b. ONE German sentence with its translation:
                            |      German: "sentence with *new_word* but no asterisks for common words"
                            |      English: Translation
                            |   c. If needed, ONE brief note about the sentence (max 2 line)
                            |   d. ONE follow-up question:
                            |      German: "question"
                            |      English: Translation
                            |4. Never give multiple examples or explanations
                        """.trimMargin()
                    )
                    messages.add(systemMessage)
                }

                // Add conversation history
                _chatState.value.messages
                    .filterNot { it.isOutOfContext }
                    .dropLast(1)  // Drop the current message since we'll add it separately
                    .forEach { msg ->
                        messages.add(Message(
                            role = if (msg.isUser) "user" else "assistant",
                            content = msg.content
                        ))
                    }

                // Add user's current message
                messages.add(Message(role = "user", content = content))

                Log.d(TAG, "=== MESSAGES BEING SENT TO AI ===")
                messages.forEachIndexed { index, msg ->
                    when (msg.role) {
                        "system" -> Log.d(TAG, "System message: [Context with ${if (_chatState.value.mode == ChatMode.CARDS_BASED) "vocabulary" else if (_chatState.value.mode == ChatMode.DIALOG_BASED) "dialogs" else if (_chatState.value.mode == ChatMode.STORY_CHAT) "vocabulary and dialogs" else "general instructions"}]")
                        else -> Log.d(TAG, "${index + 1}. ${msg.role}: ${msg.content}")
                    }
                }
                Log.d(TAG, "=== END OF MESSAGES ===")
                Log.d(TAG, "Using model: ${_chatState.value.config.selectedModel.id}")
                val response = openRouterService.sendMessage(
                    apiKey = _chatState.value.config.apiKey,
                    model = _chatState.value.config.selectedModel.id,
                    messages = messages,
                    temperature = _chatState.value.config.temperature
                )

                Log.d(TAG, "Received response: $response")

                // Add AI response
                if (response.error != null) {
                    throw Exception(response.error.message ?: "Unknown error from OpenRouter")
                }

                if (response.choices == null || response.choices.isEmpty()) {
                    throw Exception("No response received from the model")
                }

                val aiMessage = ChatMessage(
                    content = response.choices.first().message?.content ?: "No response content",
                    isUser = false
                )
                addMessage(aiMessage)

                // Update token count from API response
                val usage = response.usage
                if (usage != null) {
                    lastMessageTokens = usage.completionTokens
                    totalConversationTokens = usage.totalTokens
                    trimMessageHistoryIfNeeded()
                }

                _chatState.value = _chatState.value.copy(
                    messages = _chatState.value.messages,
                    isLoading = false
                )
                
                // Auto-read the latest message if enabled
                if (_chatState.value.messages.isNotEmpty()) {
                    autoReadMessage(_chatState.value.messages.last())
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
                _chatState.value = _chatState.value.copy(
                    error = e.message ?: "An unknown error occurred",
                    isLoading = false
                )
            } finally {
                _chatState.value = _chatState.value.copy(isLoading = false)
            }
        }
    }

    fun speakGerman(text: String, asStoryTeller: Boolean = false) {
        if (isReading) {
            mediaManager.stop()
            isReading = false
        } else {
            mediaManager.speakGerman(text, asStoryTeller)
            isReading = true
        }
    }

    fun toggleAutoRead() {
        _autoReadEnabled.value = !_autoReadEnabled.value
    }

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
        mediaManager.setSpeechRate(rate)
    }

    fun autoReadMessage(message: ChatMessage) {
        if (!_autoReadEnabled.value) return
        
        viewModelScope.launch {
            // Initial delay to let the message load
            kotlinx.coroutines.delay(1000) // 1 second initial delay

            // Collect all German sentences
            val germanSentences = message.content.split("\n")
                .filter { it.trim().startsWith("German:", ignoreCase = true) }
                .map { it.substringAfter(":").trim() }

            // Read each sentence with a delay
            germanSentences.forEachIndexed { index, germanText ->
                if (index > 0) {
                    // Add delay between sentences
                    kotlinx.coroutines.delay(2000) // 2 seconds delay between sentences
                }
                speakGerman(germanText)
            }
        }
    }

    private fun trimMessageHistoryIfNeeded() {
        val currentModel = _chatState.value.config.selectedModel
        val maxContextLength = currentModel.contextSize
        val threshold = (maxContextLength * CONTEXT_THRESHOLD_PERCENTAGE).toInt()
        
        if (totalConversationTokens > threshold) {
            Log.d(TAG, "Context length ($totalConversationTokens) exceeded threshold ($threshold) for model ${currentModel.name}")
            
            val currentMessages = _chatState.value.messages
            val messagesToKeepInContext = (currentMessages.size * MESSAGES_TO_KEEP_PERCENTAGE).toInt()
            
            // Mark older messages as out of context but keep them in UI
            val updatedMessages = currentMessages.mapIndexed { index, message ->
                if (index < currentMessages.size - messagesToKeepInContext) {
                    message.copy(isOutOfContext = true)
                } else {
                    message
                }
            }
            
            _chatState.value = _chatState.value.copy(messages = updatedMessages)
            
            // Reset token count since we'll get new count from next API call
            totalConversationTokens = 0
            
            Log.d(TAG, "Marked ${currentMessages.size - messagesToKeepInContext} messages as out of context")
        }
    }

    private fun addMessage(message: ChatMessage) {
        _chatState.value = _chatState.value.copy(
            messages = _chatState.value.messages + message
        )
    }

    fun clearMessages() {
        _chatState.value = _chatState.value.copy(
            messages = emptyList(),
            error = null
        )
    }

    fun showSaveChatDialog() {
        _showSaveChatDialog.value = true
    }

    fun hideSaveChatDialog() {
        _showSaveChatDialog.value = false
    }

    fun saveCurrentChat(name: String) {
        viewModelScope.launch {
            val savedChat = SavedChat(
                name = name,
                messages = _chatState.value.messages,
                mode = _chatState.value.mode
            )
            savedChatDao.insertSavedChat(savedChat)
            hideSaveChatDialog()
        }
    }

    fun loadSavedChat(chatId: String) {
        viewModelScope.launch {
            val savedChat = savedChatDao.getSavedChatById(chatId)
            savedChat?.let { chat ->
                _chatState.value = _chatState.value.copy(
                    messages = chat.messages,
                    mode = chat.mode
                )
            }
        }
    }

    fun deleteSavedChat(chat: SavedChat) {
        viewModelScope.launch {
            savedChatDao.deleteSavedChat(chat)
        }
    }

    fun addToFlashcards(germanText: String, englishText: String) {
        viewModelScope.launch {
            val flashCard = FlashCard(
                germanText = germanText,
                englishText = englishText,
                category = "Chat",
                phonetic = "",
                tags = emptyList(),
                examples = emptyList()
            )
            flashCardDao.insertFlashCard(flashCard)
        }
    }

    fun reinitializeTextToSpeech() {
        viewModelScope.launch {
            mediaManager.reinitialize()
        }
    }
}
