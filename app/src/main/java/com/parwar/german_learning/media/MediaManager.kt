package com.parwar.german_learning.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.speech.tts.Voice
import android.support.v4.media.MediaBrowserCompat
import com.parwar.german_learning.utils.TextToSpeechManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val textToSpeechManager: TextToSpeechManager
) {
    var mediaPlaybackService: MediaPlaybackService? = null
        private set
    private var isServiceBound = false

    // Forward the voice properties from TextToSpeechManager
    val availableGermanVoices: StateFlow<List<Voice>> = textToSpeechManager.availableGermanVoices
    val availableEnglishVoices: StateFlow<List<Voice>> = textToSpeechManager.availableEnglishVoices

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MediaPlaybackService.LocalBinder) {
                mediaPlaybackService = service.getService()
                isServiceBound = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mediaPlaybackService = null
            isServiceBound = false
            // Try to rebind if disconnected unexpectedly
            val intent = Intent(context, MediaPlaybackService::class.java)
            context.startForegroundService(intent)
            context.bindService(intent, this, Context.BIND_AUTO_CREATE)
        }
    }

    suspend fun initialize() {
        textToSpeechManager.initialize()
    }

    suspend fun reinitialize() {
        textToSpeechManager.reinitialize()
    }

    suspend fun startService() {
        // Initialize TTS first
        textToSpeechManager.initialize()
        
        val intent = Intent(context, MediaPlaybackService::class.java)
        context.startForegroundService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun stopService() {
        if (isServiceBound) {
            mediaPlaybackService?.removeMediaControlCallback()  // Remove callback when stopping service
            context.unbindService(serviceConnection)
            isServiceBound = false
        }
        val intent = Intent(context, MediaPlaybackService::class.java)
        context.stopService(intent)
        textToSpeechManager.shutdown()
    }

    fun speakGerman(text: String, asStoryTeller: Boolean = false) {
        if (asStoryTeller) {
            textToSpeechManager.speakGerman(text, true)
        } else {
            mediaPlaybackService?.speakText(text, MediaPlaybackService.Language.GERMAN)
        }
    }

    fun speakEnglish(text: String) {
        mediaPlaybackService?.speakText(text, MediaPlaybackService.Language.ENGLISH)
    }

    /**
     * Speaks German text followed by English text in sequence,
     * ensuring the German text is fully spoken before starting the English text.
     */
    fun speakSequence(germanText: String, englishText: String) {
        // Use the new sequence function in MediaPlaybackService
        mediaPlaybackService?.speakTextSequence(germanText, englishText)
    }

    fun setSpeechRate(rate: Float) {
        mediaPlaybackService?.setSpeechRate(rate)
        textToSpeechManager.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        textToSpeechManager.setPitch(pitch)
    }

    fun setGermanVoice(voice: Voice?) {
        textToSpeechManager.setGermanVoice(voice)
    }

    fun setEnglishVoice(voice: Voice?) {
        textToSpeechManager.setEnglishVoice(voice)
    }

    fun stop() {
        mediaPlaybackService?.stopPlayback()
        textToSpeechManager.stop()
    }
}
