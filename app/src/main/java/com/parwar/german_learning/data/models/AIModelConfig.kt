package com.parwar.german_learning.data.models

data class TokenCost(
    val inputCost: Double,
    val outputCost: Double
)

data class AIModelConfig(
    val maxTokens: Int,
    val cost: TokenCost
)

object AIModels {
    const val CONTEXT_THRESHOLD_PERCENTAGE = 0.8f
    const val MESSAGES_TO_KEEP_PERCENTAGE = 0.7f

    val modelConfigs = mapOf(
        "google/gemini-2.0-flash-exp:free" to AIModelConfig(
            maxTokens = 1050000,  // 1.05M context window
            cost = TokenCost(0.0, 0.0)
        ),
        "anthropic/claude-3-sonnet" to AIModelConfig(
            maxTokens = 200000,
            cost = TokenCost(3.0, 15.0)
        ),
        "deepseek-ai/deepseek-coder-33b-instruct" to AIModelConfig(
            maxTokens = 65536,
            cost = TokenCost(0.15, 0.30)
        ),
        "google/gemini-pro" to AIModelConfig(
            maxTokens = 8192,
            cost = TokenCost(0.0, 0.0)
        ),
        "openai/gpt-4" to AIModelConfig(
            maxTokens = 128000,
            cost = TokenCost(2.5, 10.0)
        ),
        "novitaai/meta-llama/llama-3.3-70b-instruct" to AIModelConfig(
            maxTokens = 131000,
            cost = TokenCost(0.39, 0.39)
        ),
        "google/gemini-2.0-flash-thinking-exp:free" to AIModelConfig(
            maxTokens = 40000,
            cost = TokenCost(0.0, 0.0)
        ),
        "deepseek/deepseek-chat-v3-0324:free" to AIModelConfig(
            maxTokens = 128000,
            cost = TokenCost(0.0, 0.0)
        ),
        "google/gemini-2.5-pro-exp-03-25:free" to AIModelConfig(
            maxTokens = 1000000,
            cost = TokenCost(0.0, 0.0)
        )
    )
}
