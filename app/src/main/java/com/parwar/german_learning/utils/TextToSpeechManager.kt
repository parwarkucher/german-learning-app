package com.parwar.german_learning.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.Set
import javax.inject.Inject
import javax.inject.Singleton
import android.speech.tts.UtteranceProgressListener
import java.util.UUID

@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var germanTTS: TextToSpeech? = null
    private var englishTTS: TextToSpeech? = null
    private var isGermanInitialized = false
    private var isEnglishInitialized = false
    private var speechRate = 0.8f
    private var isStorytellerMode = false
    private val TAG = "TextToSpeechManager"
    
    // Voice settings
    private var currentGermanVoice: Voice? = null
    private var currentEnglishVoice: Voice? = null
    private var currentPitch = 1.0f
    
    // Expose available voices
    private val _availableGermanVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableGermanVoices: StateFlow<List<Voice>> = _availableGermanVoices.asStateFlow()
    
    private val _availableEnglishVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableEnglishVoices: StateFlow<List<Voice>> = _availableEnglishVoices.asStateFlow()

    // Add a callback interface for TTS completion
    interface UtteranceCompletionCallback {
        fun onUtteranceCompleted(utteranceId: String, success: Boolean)
    }
    
    // Callback reference
    private var utteranceCompletionCallback: UtteranceCompletionCallback? = null
    
    fun setUtteranceCompletionCallback(callback: UtteranceCompletionCallback) {
        utteranceCompletionCallback = callback
    }

    suspend fun initialize() = withContext(Dispatchers.Main) {
        Log.d(TAG, "Initializing TTS engines")
        shutdown()  // Ensure clean state
        
        val germanInitDeferred = CompletableDeferred<Boolean>()
        val englishInitDeferred = CompletableDeferred<Boolean>()
        
        germanTTS = TextToSpeech(context) { status ->
            Log.d(TAG, "German TTS init status: $status")
            isGermanInitialized = status == TextToSpeech.SUCCESS
            if (isGermanInitialized) {
                germanTTS?.let { tts ->
                    tts.language = Locale.GERMAN
                    tts.setSpeechRate(speechRate)
                    
                    try {
                        // Safely get available voices with null checks
                        val allVoices = try {
                            tts.voices ?: emptySet()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting voices: ${e.message}")
                            emptySet()
                        }
                        
                        Log.d(TAG, "All voices: ${allVoices.map { "${it.name} (${it.locale})" }}")
                        
                        // Try to find any voices that can speak German, even if not perfect locale match
                        val germanVoices = allVoices.filter { voice -> 
                            try {
                                (voice.locale?.language == Locale.GERMAN.language ||
                                voice.name?.contains("de-", ignoreCase = true) == true ||
                                voice.name?.contains("german", ignoreCase = true) == true) &&
                                voice.name?.contains("network", ignoreCase = true) != true
                            } catch (e: Exception) {
                                Log.e(TAG, "Error filtering voice: ${e.message}")
                                false
                            }
                        }
                        
                        // If no German voices found, try to get any Samsung voices (they may support German)
                        val voices = if (germanVoices.isEmpty()) {
                            Log.d(TAG, "No German voices found, looking for Samsung voices")
                            allVoices.filter { voice -> 
                                try {
                                    voice.name?.contains("samsung", ignoreCase = true) == true
                                } catch (e: Exception) {
                                    false
                                }
                            }
                        } else {
                            germanVoices
                        }
                        
                        // If still empty, just use all voices
                        val finalVoices = if (voices.isEmpty()) {
                            Log.d(TAG, "No specific voices found, using all available voices")
                            allVoices.filter { voice -> 
                                try {
                                    voice.name?.contains("network", ignoreCase = true) != true
                                } catch (e: Exception) {
                                    true // Include all voices if we can't filter
                                }
                            }
                        } else {
                            voices
                        }
                        
                        _availableGermanVoices.value = finalVoices
                        
                        // Set default voice if available
                        if (finalVoices.isNotEmpty()) {
                            findAndSetBestGermanVoice(tts, finalVoices)
                            Log.d(TAG, "Available German voices: ${finalVoices.map { "${it.name} (${it.locale})" }}")
                        } else {
                            Log.d(TAG, "No voices available for German speech")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting up German TTS: ${e.message}")
                    }
                }
            }
            germanInitDeferred.complete(isGermanInitialized)
        }

        englishTTS = TextToSpeech(context) { status ->
            Log.d(TAG, "English TTS init status: $status")
            isEnglishInitialized = status == TextToSpeech.SUCCESS
            if (isEnglishInitialized) {
                englishTTS?.let { tts ->
                    tts.language = Locale.US
                    tts.setSpeechRate(speechRate)
                    
                    try {
                        // Safely get available voices with null checks
                        val allVoices = try {
                            tts.voices ?: emptySet()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting English voices: ${e.message}")
                            emptySet()
                        }
                        
                        // Try to find any voices that can speak English
                        val englishVoices = allVoices.filter { voice -> 
                            try {
                                (voice.locale?.language == Locale.ENGLISH.language || 
                                voice.locale == Locale.US ||
                                voice.name?.contains("en-", ignoreCase = true) == true ||
                                voice.name?.contains("english", ignoreCase = true) == true) && 
                                voice.name?.contains("network", ignoreCase = true) != true
                            } catch (e: Exception) {
                                Log.e(TAG, "Error filtering English voice: ${e.message}")
                                false
                            }
                        }
                        
                        // If no English voices found, try to get any Samsung voices
                        val voices = if (englishVoices.isEmpty()) {
                            Log.d(TAG, "No English voices found, looking for Samsung voices")
                            allVoices.filter { voice ->
                                try {
                                    voice.name?.contains("samsung", ignoreCase = true) == true
                                } catch (e: Exception) {
                                    false
                                }
                            }
                        } else {
                            englishVoices
                        }
                        
                        // If still empty, just use all voices
                        val finalVoices = if (voices.isEmpty()) {
                            Log.d(TAG, "No specific voices found for English, using all available voices")
                            allVoices.filter { voice ->
                                try {
                                    voice.name?.contains("network", ignoreCase = true) != true
                                } catch (e: Exception) {
                                    true // Include all voices if we can't filter
                                }
                            }
                        } else {
                            voices
                        }
                        
                        _availableEnglishVoices.value = finalVoices
                        
                        // Set default voice if available
                        if (finalVoices.isNotEmpty()) {
                            findAndSetBestEnglishVoice(tts, finalVoices)
                            Log.d(TAG, "Available English voices: ${finalVoices.map { "${it.name} (${it.locale})" }}")
                        } else {
                            Log.d(TAG, "No voices available for English speech")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting up English TTS: ${e.message}")
                    }
                }
            }
            englishInitDeferred.complete(isEnglishInitialized)
        }

        // Wait for both engines to initialize
        val germanSuccess = germanInitDeferred.await()
        val englishSuccess = englishInitDeferred.await()
        
        Log.d(TAG, "TTS initialization complete - German: $germanSuccess, English: $englishSuccess")
        return@withContext germanSuccess && englishSuccess
    }
    
    private fun findAndSetBestGermanVoice(tts: TextToSpeech, voices: List<Voice>) {
        try {
            // Try to find Samsung voices first on Samsung devices
            val samsungVoice = voices.firstOrNull { voice ->
                try {
                    voice.name?.contains("samsung", ignoreCase = true) == true
                } catch (e: Exception) {
                    false
                }
            }
            val maleVoice = voices.firstOrNull { voice ->
                try {
                    (voice.name?.contains("male", ignoreCase = true) == true || 
                    voice.name?.contains("de-de-x-deb-local", ignoreCase = true) == true) &&
                    voice.name?.contains("female", ignoreCase = true) != true
                } catch (e: Exception) {
                    false
                }
            }
            val femaleVoice = voices.firstOrNull { voice ->
                try {
                    voice.name?.contains("female", ignoreCase = true) == true
                } catch (e: Exception) {
                    false
                }
            }
            
            // Prioritize in this order: Samsung voice, male voice, any voice
            when {
                samsungVoice != null -> {
                    try {
                        tts.voice = samsungVoice
                        currentGermanVoice = samsungVoice
                        Log.d(TAG, "Set German voice to Samsung: ${samsungVoice.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting Samsung voice: ${e.message}")
                    }
                }
                maleVoice != null -> {
                    try {
                        tts.voice = maleVoice
                        currentGermanVoice = maleVoice
                        Log.d(TAG, "Set German voice to male: ${maleVoice.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting male voice: ${e.message}")
                    }
                }
                femaleVoice != null -> {
                    try {
                        tts.voice = femaleVoice
                        currentGermanVoice = femaleVoice
                        Log.d(TAG, "Set German voice to female: ${femaleVoice.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting female voice: ${e.message}")
                    }
                }
                voices.isNotEmpty() -> {
                    try {
                        val firstVoice = voices.first()
                        tts.voice = firstVoice
                        currentGermanVoice = firstVoice
                        Log.d(TAG, "Set German voice to: ${firstVoice.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting default voice: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in findAndSetBestGermanVoice: ${e.message}")
        }
    }
    
    private fun findAndSetBestEnglishVoice(tts: TextToSpeech, voices: List<Voice>) {
        try {
            // Try to find Samsung voices first on Samsung devices
            val samsungVoice = voices.firstOrNull { voice ->
                try {
                    voice.name?.contains("samsung", ignoreCase = true) == true
                } catch (e: Exception) {
                    false
                }
            }
            val maleVoice = voices.firstOrNull { voice ->
                try {
                    (voice.name?.contains("male", ignoreCase = true) == true || 
                    voice.name?.contains("en-us-x-sfg-local", ignoreCase = true) == true) &&
                    voice.name?.contains("female", ignoreCase = true) != true
                } catch (e: Exception) {
                    false
                }
            }
            val femaleVoice = voices.firstOrNull { voice ->
                try {
                    voice.name?.contains("female", ignoreCase = true) == true
                } catch (e: Exception) {
                    false
                }
            }
            
            // Prioritize in this order: Samsung voice, female voice, any voice
            when {
                samsungVoice != null -> {
                    try {
                        tts.voice = samsungVoice
                        currentEnglishVoice = samsungVoice
                        Log.d(TAG, "Set English voice to Samsung: ${samsungVoice.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting Samsung voice: ${e.message}")
                    }
                }
                femaleVoice != null -> {
                    try {
                        tts.voice = femaleVoice
                        currentEnglishVoice = femaleVoice
                        Log.d(TAG, "Set English voice to female: ${femaleVoice.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting female voice: ${e.message}")
                    }
                }
                maleVoice != null -> {
                    try {
                        tts.voice = maleVoice
                        currentEnglishVoice = maleVoice
                        Log.d(TAG, "Set English voice to male: ${maleVoice.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting male voice: ${e.message}")
                    }
                }
                voices.isNotEmpty() -> {
                    try {
                        val firstVoice = voices.first()
                        tts.voice = firstVoice
                        currentEnglishVoice = firstVoice
                        Log.d(TAG, "Set English voice to: ${firstVoice.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting default voice: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in findAndSetBestEnglishVoice: ${e.message}")
        }
    }

    suspend fun reinitialize() = withContext(Dispatchers.Main) {
        Log.d(TAG, "Reinitializing TTS")
        shutdown()
        delay(100) // Small delay to ensure clean shutdown
        initialize()
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate
        germanTTS?.setSpeechRate(rate)
        englishTTS?.setSpeechRate(rate)
    }
    
    fun setPitch(pitch: Float) {
        currentPitch = pitch
        germanTTS?.setPitch(pitch)
        englishTTS?.setPitch(pitch)
    }
    
    fun setGermanVoice(voice: Voice?) {
        if (voice != null && isGermanInitialized) {
            try {
                germanTTS?.voice = voice
                currentGermanVoice = voice
                val voiceName = try { voice.name } catch (e: Exception) { "unknown" }
                Log.d(TAG, "Set new German voice: $voiceName")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting German voice: ${e.message}")
            }
        }
    }
    
    fun setEnglishVoice(voice: Voice?) {
        if (voice != null && isEnglishInitialized) {
            try {
                englishTTS?.voice = voice
                currentEnglishVoice = voice
                val voiceName = try { voice.name } catch (e: Exception) { "unknown" }
                Log.d(TAG, "Set new English voice: $voiceName")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting English voice: ${e.message}")
            }
        }
    }

    fun setStorytellerMode(enabled: Boolean) {
        isStorytellerMode = enabled
        germanTTS?.let { tts ->
            if (enabled) {
                // Slower rate for better storytelling
                tts.setSpeechRate(0.85f)
                // Slightly lower pitch for a warmer voice
                tts.setPitch(0.9f)
            } else {
                tts.setSpeechRate(speechRate)
                tts.setPitch(currentPitch)
            }
        }
    }

    fun speakGerman(text: String, asStoryTeller: Boolean = false, externalUtteranceId: String? = null) {
        Log.d(TAG, "Attempting to speak German text: $text")
        if (!isGermanInitialized) {
            Log.e(TAG, "German TTS not initialized")
            return
        }
        germanTTS?.let { tts ->
            tts.stop()
            if (asStoryTeller) {
                setStorytellerMode(true)
            }
            val utteranceId = externalUtteranceId ?: UUID.randomUUID().toString()
            Log.d(TAG, "Using German utterance ID: $utteranceId")
            
            // Add a completion listener
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "German TTS started: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "German TTS completed: $utteranceId")
                    utteranceId?.let {
                        utteranceCompletionCallback?.onUtteranceCompleted(it, true)
                    }
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "German TTS error: $utteranceId")
                    utteranceId?.let {
                        utteranceCompletionCallback?.onUtteranceCompleted(it, false)
                    }
                }
            })
            
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (asStoryTeller) {
                setStorytellerMode(false)
            }
            Log.d(TAG, "German TTS speak result: $result")
        } ?: Log.e(TAG, "German TTS is null")
    }

    fun speakEnglish(text: String, externalUtteranceId: String? = null) {
        Log.d(TAG, "Attempting to speak English text: $text")
        if (!isEnglishInitialized) {
            Log.e(TAG, "English TTS not initialized")
            return
        }
        englishTTS?.let { tts ->
            tts.stop()
            val utteranceId = externalUtteranceId ?: UUID.randomUUID().toString()
            Log.d(TAG, "Using English utterance ID: $utteranceId")
            
            // Add a completion listener
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "English TTS started: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "English TTS completed: $utteranceId")
                    utteranceId?.let {
                        utteranceCompletionCallback?.onUtteranceCompleted(it, true)
                    }
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "English TTS error: $utteranceId")
                    utteranceId?.let {
                        utteranceCompletionCallback?.onUtteranceCompleted(it, false)
                    }
                }
            })
            
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            Log.d(TAG, "English TTS speak result: $result")
        } ?: Log.e(TAG, "English TTS is null")
    }

    fun stop() {
        germanTTS?.stop()
        englishTTS?.stop()
    }

    fun shutdown() {
        Log.d(TAG, "Shutting down TTS")
        stop()
        germanTTS?.shutdown()
        englishTTS?.shutdown()
        germanTTS = null
        englishTTS = null
        isGermanInitialized = false
        isEnglishInitialized = false
    }
}
