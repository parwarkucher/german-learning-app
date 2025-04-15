package com.parwar.german_learning.data.models

import com.parwar.german_learning.network.ModelInfo
import com.parwar.german_learning.network.ModelPricing

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isOutOfContext: Boolean = false
)

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val mode: ChatMode = ChatMode.CARDS_BASED,
    val config: ChatConfig = ChatConfig(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val cardRange: CardRange = CardRange(),
    val totalConversationTokens: Int = 0,
    val lastMessageTokens: Int = 0,
    val selectedModel: String = "google/gemini-2.0-flash-thinking-exp:free"
)

data class CardRange(
    val startIndex: Int = 0,  // 0 means from beginning
    val endIndex: Int = Int.MAX_VALUE,  // MAX_VALUE means until end
    val enabled: Boolean = false  // If false, send all cards
)

data class ChatConfig(
    val apiKey: String = "",
    val selectedModel: ModelInfo = ModelInfo(
        id = "google/gemini-2.0-flash-thinking-exp:free",
        name = "Gemini 2.0 Flash Thinking",
        contextSize = 40_000,
        pricing = ModelPricing(0.0, 0.0)
    ),
    val temperature: Float = 0.7f
)

enum class ChatMode {
    GENERAL,
    CARDS_BASED,
    DIALOG_BASED,
    CARDS_AND_DIALOG_BASED,
    STORY_CHAT
}
