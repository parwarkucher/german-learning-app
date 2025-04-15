package com.parwar.german_learning.network

import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

data class ModelInfo(
    val id: String,
    val name: String,
    val contextSize: Int,
    val pricing: ModelPricing
)

data class ModelPricing(
    val inputPrice: Double,  // Price per million tokens
    val outputPrice: Double  // Price per million tokens
)

data class ImageUrl(
    @SerializedName("url") val url: String
)

data class MessageContent(
    @SerializedName("type") val type: String,
    @SerializedName("text") val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
)

data class Message(
    val role: String,
    val content: String,  // Keep as String for backward compatibility
    @SerializedName("content_parts") val contentParts: List<MessageContent>? = null  // Optional field for multimodal content
)

data class Choice(
    @SerializedName("message") val message: Message,
    @SerializedName("index") val index: Int = 0,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
)

data class OpenRouterErrorMetadata(
    @SerializedName("raw") val raw: Any?,
    @SerializedName("provider_name") val providerName: String?
)

data class OpenRouterError(
    @SerializedName("message") val message: String?,
    @SerializedName("code") val code: Int?,
    @SerializedName("metadata") val metadata: OpenRouterErrorMetadata?
)

data class OpenRouterResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("choices") val choices: List<Choice>?,
    @SerializedName("created") val created: Long,
    @SerializedName("model") val model: String?,
    @SerializedName("usage") val usage: Usage?,
    @SerializedName("error") val error: OpenRouterError?
)

data class Provider(
    @SerializedName("order") val order: List<String>,
    @SerializedName("allow_fallbacks") val allowFallbacks: Boolean = false
)

data class OpenRouterRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Float = 0.7f,
    val provider: Provider? = null
)

interface OpenRouterApi {
    @POST("chat/completions")
    suspend fun chat(
        @Header("Authorization") apiKey: String,
        @Header("HTTP-Referer") referer: String = "https://github.com/parwarr/german-learning-android",
        @Header("X-Title") title: String = "German Learning App",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: OpenRouterRequest
    ): OpenRouterResponse
}

@Singleton
class OpenRouterService @Inject constructor() {
    private val TAG = "OpenRouterService"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://openrouter.ai/api/v1/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api: OpenRouterApi = retrofit.create(OpenRouterApi::class.java)

    suspend fun sendMessage(
        apiKey: String,
        model: String,
        messages: List<Message>,
        temperature: Float,
        provider: String? = null,
        maxRetries: Int = 5
    ): OpenRouterResponse = withContext(Dispatchers.IO) {
        var retryCount = 0
        var lastError: Exception? = null

        while (retryCount < maxRetries) {
            try {
                val modelId = if (model == "novitaai/meta-llama/llama-3.3-70b-instruct") "meta-llama/llama-3.3-70b-instruct" else model
                
                // Set provider based on model
                val effectiveProvider = when {
                    modelId == "meta-llama/llama-3.3-70b-instruct" -> Provider(listOf("Novita"), false)
                    modelId.startsWith("google/") -> Provider(listOf("Google"), true)
                    modelId.startsWith("deepseek/") -> Provider(listOf("DeepSeek"), true)
                    provider != null -> Provider(listOf(provider))
                    else -> null
                }
                
                Log.d(TAG, "Attempt ${retryCount + 1}/$maxRetries: Sending message to model $modelId")
                val request = OpenRouterRequest(
                    model = modelId,
                    messages = messages,
                    temperature = temperature,
                    provider = effectiveProvider
                )
                val response = api.chat(
                    apiKey = "Bearer $apiKey",
                    request = request
                )

                // Check for error in response
                if (response.error != null) {
                    val errorMessage = response.error.message ?: "Unknown error"
                    val errorCode = response.error.code
                    val provider = response.error.metadata?.providerName

                    // Handle rate limit errors specially
                    if (errorCode == 429 || errorMessage.contains("rate limit", ignoreCase = true)) {
                        retryCount++
                        if (retryCount < maxRetries) {
                            val delayMs = 1000L * (1 shl retryCount) // Exponential backoff
                            Log.d(TAG, "Rate limit hit from provider $provider. Retrying in ${delayMs}ms...")
                            delay(delayMs)
                            continue
                        }
                    }

                    // Convert error to user-friendly message
                    throw Exception(getUserFriendlyError(errorCode, errorMessage, provider))
                }

                // Log successful response details
                response.usage?.let { usage ->
                    Log.d(TAG, "Response received. Tokens used - Prompt: ${usage.promptTokens}, " +
                            "Completion: ${usage.completionTokens}, Total: ${usage.totalTokens}")
                }

                return@withContext response

            } catch (e: Exception) {
                lastError = e
                Log.e(TAG, "Error during API call (attempt ${retryCount + 1}/$maxRetries): ${e.message}")

                // Check if it's worth retrying
                if (shouldRetry(e)) {
                    retryCount++
                    if (retryCount < maxRetries) {
                        val delayMs = 1000L * (1 shl retryCount)
                        Log.d(TAG, "Retrying in ${delayMs}ms...")
                        delay(delayMs)
                        continue
                    }
                }

                // If we shouldn't retry or have exhausted retries, throw with user-friendly message
                throw Exception(getUserFriendlyError(null, e.message, null))
            }
        }

        // If we've exhausted all retries
        throw lastError ?: Exception("Failed to get a response after $maxRetries attempts")
    }

    private fun shouldRetry(error: Exception): Boolean {
        val message = error.message?.lowercase() ?: return false
        return message.contains("429") || 
               message.contains("rate limit") ||
               message.contains("timeout") ||
               message.contains("connection") ||
               message.contains("temporary")
    }

    private fun getUserFriendlyError(code: Int?, message: String?, provider: String?): String {
        val providerInfo = if (provider != null) " ($provider)" else ""
        return when {
            code == 429 || message?.contains("rate limit", ignoreCase = true) == true ->
                "You've made too many requests. Please wait a moment and try again."
            code == 400 ->
                "Oops! Something went wrong with the input. Please check and try again."
            code == 401 || code == 403 || message?.contains("api key", ignoreCase = true) == true ->
                "Access denied. Please ensure your account is properly authenticated."
            code == 404 ->
                "The requested resource could not be found. Please check and try again."
            code == 500 ->
                "The service is temporarily unavailable. Please try again later."
            code in listOf(502, 503, 504) ->
                "The server is currently unavailable. Please try again after some time."
            code == 418 ->
                "Request rate exceeded. Please slow down and try again soon."
            code == 409 ->
                "This action cannot be completed due to a conflict. Please check your request."
            code == 422 ->
                "The request could not be processed. Please review your input."
            code == 402 ->
                "Your subscription or credits have expired. Please update your payment information."
            message?.contains("context length", ignoreCase = true) == true ->
                "The conversation is too long. Some older messages will be removed to continue."
            else -> "An error occurred while getting a response: ${message ?: "Unknown error"}"
        }
    }

    companion object {
        val AVAILABLE_MODELS = listOf(
            ModelInfo(
                id = "google/gemini-2.0-flash-exp:free",
                name = "Gemini Flash 2.0 (free)",
                contextSize = 1050000,  // 1.05M context window
                pricing = ModelPricing(0.0, 0.0)
            ),
            ModelInfo(
                id = "google/gemini-2.0-flash-thinking-exp:free",
                name = "Gemini 2.0 Flash Thinking",
                contextSize = 40000,
                pricing = ModelPricing(0.0, 0.0)
            ),
            ModelInfo(
                id = "meta-llama/llama-3.3-70b-instruct",
                name = "Llama 3.3 70B",
                contextSize = 131000,
                pricing = ModelPricing(0.39, 0.39)
            ),
            ModelInfo(
                id = "meta-llama/llama-3.1-405b-instruct:free",
                name = "META 3.1 405B (free)",
                contextSize = 8192,
                pricing = ModelPricing(0.0, 0.0)
            ),
            ModelInfo(
                id = "anthropic/claude-3.5-sonnet",
                name = "Claude 3.5 Sonnet",
                contextSize = 200000,
                pricing = ModelPricing(3.0, 15.0)
            ),
            ModelInfo(
                id = "deepseek/deepseek-chat",
                name = "DeepSeek V2.5",
                contextSize = 65536,
                pricing = ModelPricing(0.15, 0.30)
            ),
            ModelInfo(
                id = "google/gemini-exp-1114:free",
                name = "Gemini Experimental 1114",
                contextSize = 8192,
                pricing = ModelPricing(0.0, 0.0)
            ),
            ModelInfo(
                id = "openai/chatgpt-4o-latest",
                name = "GPT-4o",
                contextSize = 128000,
                pricing = ModelPricing(2.5, 10.0)
            ),
            ModelInfo(
                id = "meta-llama/llama-3.1-70b-instruct:free",
                name = "META 3.1 70B (Free)",
                contextSize = 8192,
                pricing = ModelPricing(0.0, 0.0)
            ),
            ModelInfo(
                id = "anthropic/claude-3.5-haiku-20241022",
                name = "Claude 3.5 Haiku",
                contextSize = 200000,
                pricing = ModelPricing(1.0, 5.0)
            ),
            ModelInfo(
                id = "qwen/qwq-32b-preview",
                name = "Qwen 32B",
                contextSize = 33000,
                pricing = ModelPricing(1.2, 1.2)
            ),
            ModelInfo(
                id = "deepseek/deepseek-r1:free",
                name = "DeepSeek: R1 (free)",
                contextSize = 164000,
                pricing = ModelPricing(0.0, 0.0)
            ),
            ModelInfo(
                id = "deepseek/deepseek-r1",
                name = "DeepSeek: R1",
                contextSize = 64000,
                pricing = ModelPricing(0.55, 2.19)
            ),
            ModelInfo(
                id = "deepseek/deepseek-chat-v3-0324:free",
                name = "DeepSeek V3 0324 (free)",
                contextSize = 128000,
                pricing = ModelPricing(0.0, 0.0)
            ),
            ModelInfo(
                id = "google/gemini-2.5-pro-exp-03-25:free",
                name = "Google: Gemini Pro 2.5 Experimental (free)",
                contextSize = 1000000,
                pricing = ModelPricing(0.0, 0.0)
            )
        )

        fun getModelIds(): List<String> = AVAILABLE_MODELS.map { it.id }
    }
}
