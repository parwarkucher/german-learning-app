package com.parwar.german_learning.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.parwar.german_learning.MainActivity
import com.parwar.german_learning.R
import com.parwar.german_learning.utils.TextToSpeechManager
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.GuardedBy
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "MediaPlaybackService"

@AndroidEntryPoint
class MediaPlaybackService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlaybackService = this@MediaPlaybackService
    }

    private val binder = LocalBinder()

    @Inject
    lateinit var textToSpeechManager: TextToSpeechManager

    private lateinit var ttsMediaSession: TTSMediaSession
    var mediaControlCallback: MediaControlCallback? = null
        private set

    private var isPlaying = false
    private var currentText: String? = null
    private var currentLanguage: Language = Language.GERMAN

    private val channelId = "GermanLearningChannel"
    private val notificationId = 1

    enum class Language {
        GERMAN, ENGLISH
    }

    private lateinit var wakeLock: PowerManager.WakeLock

    // Add tracking for utterance IDs and a flag for playback sequence
    private val pendingUtterances = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var currentGermanUtteranceId: String? = null
    private var pendingEnglishText: String? = null
    private var processingSequence = false

    companion object {
        private const val TAG = "MediaPlaybackService"
        private var savedCallback: MediaControlCallback? = null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
        
        ttsMediaSession = TTSMediaSession(this)
        ttsMediaSession.setActive(true)
        
        // Restore saved callback if exists
        savedCallback?.let {
            Log.d(TAG, "Restoring saved callback")
            mediaControlCallback = it
            startForeground(notificationId, createNotification())
        }

        Log.d(TAG, "TTSMediaSession created and activated")

        // Initialize wake lock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GermanLearning::MediaPlaybackWakeLock"
        )
        
        // Set up the utterance completion callback
        textToSpeechManager.setUtteranceCompletionCallback(object : TextToSpeechManager.UtteranceCompletionCallback {
            override fun onUtteranceCompleted(utteranceId: String, success: Boolean) {
                // Call the class method using this@ qualifier to avoid recursion
                this@MediaPlaybackService.onUtteranceCompleted(utteranceId, success)
            }
        })
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start as foreground service immediately if not already
        if (!this::ttsMediaSession.isInitialized) {
            onCreate()
        }
        
        when (intent?.action) {
            "PLAY" -> {
                mediaControlCallback?.onPlayPause()
                updateNotificationState()
            }
            "PAUSE" -> {
                mediaControlCallback?.onPlayPause()
                updateNotificationState()
            }
            "STOP_SERVICE" -> stopPlayback()
            "PREVIOUS" -> {
                mediaControlCallback?.onPrevious()
                updateNotificationState()
            }
            "NEXT" -> {
                mediaControlCallback?.onNext()
                updateNotificationState()
            }
        }
        
        // Always ensure we're in foreground
        startForeground(notificationId, createNotification())
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "German Learning"
            val descriptionText = "German Learning Audio Playback"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                setSound(null, null)  // Allow system sound settings to be used
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = if (isPlaying) "PAUSE" else "PLAY"
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = "PREVIOUS"
        }
        val prevPendingIntent = PendingIntent.getService(
            this, 3, prevIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = "NEXT"
        }
        val nextPendingIntent = PendingIntent.getService(
            this, 4, nextIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create notification content text based on what's being spoken
        val contentText = when {
            // If processing sequence and speaking English, show both texts
            processingSequence && currentLanguage == Language.ENGLISH && pendingEnglishText != null -> 
                "${currentText ?: ""} - ${pendingEnglishText ?: ""}"
            
            // If processing sequence but still on German, show only German
            processingSequence && currentLanguage == Language.GERMAN -> 
                currentText ?: ""
                
            // Otherwise just show current text
            else -> currentText ?: "Ready to play"
        }

        // Create the media style
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(ttsMediaSession.getSessionToken())
            .setShowActionsInCompactView(0, 1, 2) // Show prev, play/pause, next buttons in compact view

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("German Learning")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_play)
            .setOngoing(isPlaying)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_previous, "Previous", prevPendingIntent)
            .addAction(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play, 
                      if (isPlaying) "Pause" else "Play", 
                      playPausePendingIntent)
            .addAction(R.drawable.ic_next, "Next", nextPendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun speakText(text: String, language: Language) {
        Log.d(TAG, "Speaking text: $text in language: $language")
        currentText = text
        currentLanguage = language
        ttsMediaSession.updateMetadata(text)
        
        // Stop any ongoing speech and ensure clean state
        textToSpeechManager.stop()
        
        // Launch coroutine to handle TTS
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Reinitialize TTS to ensure clean state
                val initSuccess = textToSpeechManager.reinitialize()
                if (!initSuccess) {
                    Log.e(TAG, "Failed to initialize TTS")
                    return@launch
                }
                
                if (language == Language.GERMAN) {
                    // Store the text to be spoken in English after German is done
                    pendingEnglishText = null
                    processingSequence = false
                }
                
                playCurrentText()
            } catch (e: Exception) {
                Log.e(TAG, "Error in speakText", e)
            }
        }
    }

    fun speakTextSequence(germanText: String, englishText: String) {
        Log.d(TAG, "Speaking sequence - German: $germanText, English: $englishText")
        
        // Stop any ongoing speech immediately
        textToSpeechManager.stop()
        
        // Store the English text to be spoken after German is done
        pendingEnglishText = englishText
        processingSequence = true
        
        currentText = germanText
        currentLanguage = Language.GERMAN
        
        // Update the metadata with only German text initially
        ttsMediaSession.updateMetadata(germanText)
        updateNotificationState()
        
        // Launch coroutine to handle TTS sequence
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Ensure TTS is in a clean state before starting
                delay(100) // Short delay to ensure previous speech is stopped
                
                isPlaying = true
                ttsMediaSession.updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                
                // Acquire wake lock if not held
                if (!wakeLock.isHeld) {
                    wakeLock.acquire(10*60*1000L) // 10 minutes timeout
                }
                
                Log.d(TAG, "Playing German text in sequence: $germanText")
                
                // Generate a unique utterance ID for tracking
                val germanUtteranceId = UUID.randomUUID().toString()
                currentGermanUtteranceId = germanUtteranceId
                Log.d(TAG, "Created German utterance ID: $germanUtteranceId")
                
                // Register completion listener before speaking
                val germanCompleted = CompletableDeferred<Boolean>()
                pendingUtterances[germanUtteranceId] = germanCompleted
                
                // Speak German text with the generated utterance ID
                textToSpeechManager.speakGerman(germanText, false, germanUtteranceId)
                
                // Wait for German speech to complete before proceeding to English
                Log.d(TAG, "Waiting for German speech to complete")
                val germanSuccess = germanCompleted.await()
                pendingUtterances.remove(germanUtteranceId)
                Log.d(TAG, "German speech completed with success: $germanSuccess")
                
                if (germanSuccess) {
                    // After German is done, proceed to English with a small delay
                    Log.d(TAG, "German TTS completed, proceeding to English: $englishText")
                    delay(300) // 300ms delay between German and English
                    
                    currentText = englishText
                    currentLanguage = Language.ENGLISH
                    
                    // Now update metadata to show both German and English
                    ttsMediaSession.updateMetadata("$germanText - $englishText")
                    updateNotificationState() // Update notification to show both texts
                    
                    // Generate a new utterance ID for English
                    val englishUtteranceId = UUID.randomUUID().toString()
                    Log.d(TAG, "Created English utterance ID: $englishUtteranceId")
                    val englishCompleted = CompletableDeferred<Boolean>()
                    pendingUtterances[englishUtteranceId] = englishCompleted
                    
                    // Directly speak English text with the generated utterance ID
                    Log.d(TAG, "Speaking English text now")
                    textToSpeechManager.speakEnglish(englishText, englishUtteranceId)
                    
                    try {
                        // Wait for English speech to complete (optional)
                        Log.d(TAG, "Waiting for English speech to complete")
                        val englishSuccess = englishCompleted.await()
                        Log.d(TAG, "English TTS completed with success: $englishSuccess")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error waiting for English TTS completion", e)
                    }
                } else {
                    Log.e(TAG, "German TTS failed, skipping English text")
                }
                
                updateNotificationState()
            } catch (e: Exception) {
                Log.e(TAG, "Error in speakTextSequence", e)
                stopPlayback()
            }
        }
    }

    private fun playCurrentText() {
        if (currentText == null) {
            Log.d(TAG, "No text to play")
            return
        }

        try {
            isPlaying = true
            ttsMediaSession.updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            
            // Acquire wake lock if not held
            if (!wakeLock.isHeld) {
                wakeLock.acquire(10*60*1000L) // 10 minutes timeout
            }
            
            Log.d(TAG, "Playing text: $currentText in language: $currentLanguage")
            
            // Generate a unique utterance ID for tracking
            val utteranceId = UUID.randomUUID().toString()
            Log.d(TAG, "Created utterance ID for playCurrentText: $utteranceId")
            
            when (currentLanguage) {
                Language.GERMAN -> {
                    // Update metadata with only German text
                    ttsMediaSession.updateMetadata(currentText!!)
                    
                    // Register a listener for TTS completion
                    if (processingSequence) {
                        currentGermanUtteranceId = utteranceId
                        registerUtteranceCompletionListener(utteranceId) { success ->
                            if (success && pendingEnglishText != null) {
                                // After German is done, speak the English text
                                Log.d(TAG, "German TTS completed, proceeding to English: $pendingEnglishText")
                                val germanText = currentText // Save current German text
                                currentText = pendingEnglishText
                                currentLanguage = Language.ENGLISH
                                
                                // Update with both German and English text
                                ttsMediaSession.updateMetadata("$germanText - $pendingEnglishText")
                                updateNotificationState() // Update notification to show both texts
                                
                                // Delay a bit to ensure systems catch up
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(300) // 300ms delay between German and English
                                    playCurrentText()
                                }
                            }
                        }
                    }
                    
                    textToSpeechManager.speakGerman(currentText!!, false, utteranceId)
                }
                Language.ENGLISH -> {
                    textToSpeechManager.speakEnglish(currentText!!, utteranceId)
                }
            }
            
            updateNotificationState()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing text", e)
            stopPlayback()
        }
    }
    
    private fun registerUtteranceCompletionListener(utteranceId: String, callback: (Boolean) -> Unit) {
        val deferred = CompletableDeferred<Boolean>()
        pendingUtterances[utteranceId] = deferred
        
        // Launch a coroutine to wait for the utterance to complete
        CoroutineScope(Dispatchers.Default).launch {
            val result = deferred.await()
            callback(result)
            pendingUtterances.remove(utteranceId)
        }
    }
    
    // Called from TextToSpeechManager when an utterance is completed
    fun onUtteranceCompleted(utteranceId: String, success: Boolean) {
        Log.d(TAG, "Utterance completed: $utteranceId, success: $success")
        
        val deferred = pendingUtterances[utteranceId]
        if (deferred != null) {
            Log.d(TAG, "Found pending utterance, completing with success: $success")
            deferred.complete(success)
        } else {
            Log.e(TAG, "No pending utterance found for ID: $utteranceId")
        }
    }

    fun pausePlayback() {
        isPlaying = false
        textToSpeechManager.stop()
        ttsMediaSession.updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        updateNotificationState()
    }

    private fun updateNotificationState() {
        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    fun stopPlayback() {
        isPlaying = false
        textToSpeechManager.stop()
        ttsMediaSession.updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        stopService()
    }

    fun stopService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    fun setSpeechRate(rate: Float) {
        textToSpeechManager.setSpeechRate(rate)
    }

    fun setMediaControlCallback(callback: MediaControlCallback) {
        Log.d(TAG, "Setting media control callback")
        savedCallback = callback  // Save callback for service recreation
        mediaControlCallback = callback
        
        if (!this::ttsMediaSession.isInitialized) {
            Log.d(TAG, "TTSMediaSession not initialized yet")
            onCreate()
        }
        
        startForeground(notificationId, createNotification())
    }

    fun removeMediaControlCallback() {
        Log.d(TAG, "Removing media control callback")
        savedCallback = null
        mediaControlCallback = null
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        // Don't clear savedCallback here to persist through recreation
        mediaControlCallback = null
        textToSpeechManager.stop()
        ttsMediaSession.release()
        super.onDestroy()
    }
}
