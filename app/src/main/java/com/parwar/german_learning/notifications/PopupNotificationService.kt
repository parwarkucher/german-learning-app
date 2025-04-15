package com.parwar.german_learning.notifications

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import com.parwar.german_learning.data.dao.DialogDao
import com.parwar.german_learning.data.dao.FlashCardDao
import com.parwar.german_learning.data.models.Dialog
import com.parwar.german_learning.data.models.FlashCard
import com.parwar.german_learning.data.models.PopupSettings
import com.parwar.german_learning.utils.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.*
import javax.inject.Inject
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.parwar.german_learning.R
import com.parwar.german_learning.MainActivity
import kotlinx.coroutines.flow.first
import android.provider.Settings
import android.media.AudioAttributes

@AndroidEntryPoint
class PopupNotificationService : Service() {
    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var flashCardDao: FlashCardDao

    @Inject
    lateinit var dialogDao: DialogDao

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var notificationJob: Job? = null
    private var currentCardIndex = -1
    private var currentDialogIndex = -1
    private var currentDialogPairIndex = -1  // Added to track current pair within dialog
    private val shownCardIds = mutableSetOf<Long>()
    private val shownDialogIds = mutableSetOf<Long>()
    private var isShowingDialogs = false
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        private const val CHANNEL_ID = "german_learning_popup"
        private const val WEAR_GROUP_KEY = "com.parwar.german_learning.WEAR_GROUP"
        private const val FOREGROUND_SERVICE_ID = 1
        private const val MAX_ACTIVE_NOTIFICATIONS = 20  // Keep 20 most recent notifications
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_POPUP_SERVICE" -> {
                acquireWakeLock()
                shownCardIds.clear()
                shownDialogIds.clear()
                isShowingDialogs = false
                startPopupScheduler()
            }
            "STOP_POPUP_SERVICE" -> {
                releaseWakeLock()
                stopPopupScheduler()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "German Learning Popups"
            val descriptionText = "Shows German learning cards as popups"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
                setAllowBubbles(true)  // Enable notification bubbles if supported
                // Set default notification sound
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        wakeLock?.release()
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GermanLearning::PopupServiceLock"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun startForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("German Learning")
            .setContentText("Popup service is running")
            .setSmallIcon(R.drawable.ic_cards)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setLocalOnly(false)
            .setGroup(WEAR_GROUP_KEY)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
            .addAction(
                R.drawable.ic_cards,
                "Open App",
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

        startForeground(FOREGROUND_SERVICE_ID, notification)
    }

    private fun startPopupScheduler() {
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            while (isActive) {
                try {
                    val settings = preferencesManager.getPopupSettings()
                    if (settings.isEnabled && isWithinActiveHours(settings)) {
                        showPopupNotification()
                    }
                    delay(settings.frequency * 60 * 1000L)
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(60 * 1000L) // Wait a minute before retrying
                }
            }
        }
    }

    private fun stopPopupScheduler() {
        notificationJob?.cancel()
        notificationJob = null
        stopSelf()
    }

    private fun isWithinActiveHours(settings: PopupSettings): Boolean {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        val startTimeInMinutes = settings.startTime * 60
        val endTimeInMinutes = settings.endTime * 60
        
        return if (endTimeInMinutes > startTimeInMinutes) {
            currentTimeInMinutes in startTimeInMinutes..endTimeInMinutes
        } else {
            currentTimeInMinutes >= startTimeInMinutes || currentTimeInMinutes <= endTimeInMinutes
        }
    }

    private suspend fun showPopupNotification() {
        val settings = preferencesManager.getPopupSettings()
        android.util.Log.d("PopupService", "ShowPopup - isShowingDialogs: $isShowingDialogs, switchToDialogs: ${settings.switchToDialogsAfterCards}")
        
        if (!isShowingDialogs) {
            // Show Flashcards
            val cards = flashCardDao.getAllFlashCards().first()
            android.util.Log.d("PopupService", "Cards - Total: ${cards.size}, Shown: ${shownCardIds.size}, CurrentIndex: $currentCardIndex")
            if (cards.isEmpty()) return

            val card = if (settings.useRandomCards) {
                val remainingCards = cards.filter { it.id !in shownCardIds }
                android.util.Log.d("PopupService", "Random Mode - Remaining Cards: ${remainingCards.size}")
                if (remainingCards.isEmpty()) {
                    android.util.Log.d("PopupService", "No remaining cards, switchToDialogs: ${settings.switchToDialogsAfterCards}")
                    if (settings.switchToDialogsAfterCards) {
                        isShowingDialogs = true
                        currentDialogIndex = settings.startDialog  // Start from the selected dialog (no -1 needed here)
                        currentDialogPairIndex = -1
                        shownDialogIds.clear()
                        android.util.Log.d("PopupService", "Switching to dialogs mode, starting from dialog ${settings.startDialog + 1}")
                        showPopupNotification()  // Recursively call to show dialog
                        return
                    } else {
                        android.util.Log.d("PopupService", "Resetting cards")
                        shownCardIds.clear()
                        cards.random()
                    }
                } else {
                    remainingCards.random()
                }
            } else {
                if (currentCardIndex == -1) {
                    currentCardIndex = settings.startCard
                }
                
                if (currentCardIndex >= cards.size) {
                    android.util.Log.d("PopupService", "Sequential Mode - Reached end, switchToDialogs: ${settings.switchToDialogsAfterCards}")
                    if (settings.switchToDialogsAfterCards) {
                        isShowingDialogs = true
                        currentDialogIndex = settings.startDialog  // Don't subtract 1 here
                        currentDialogPairIndex = -1
                        shownDialogIds.clear()
                        android.util.Log.d("PopupService", "Switching to dialogs mode, starting from dialog ${settings.startDialog + 1}")
                        showPopupNotification()  // Recursively call to show dialog
                        return
                    } else {
                        currentCardIndex = 0
                    }
                }
                
                val selectedCard = cards[currentCardIndex]
                currentCardIndex++
                selectedCard
            }

            if (settings.useRandomCards) {
                shownCardIds.add(card.id)
            }

            android.util.Log.d("PopupService", "Showing card: ${card.germanText}")
            showCardNotification(card)
        } else {
            // Show Dialogs
            val dialogs = dialogDao.getAllDialogs().first()
            android.util.Log.d("PopupService", "Dialogs - Total: ${dialogs.size}, Shown: ${shownDialogIds.size}, CurrentIndex: $currentDialogIndex")
            
            if (dialogs.isEmpty()) {
                android.util.Log.d("PopupService", "No dialogs available, switching back to cards")
                // If no dialogs available, switch back to cards
                isShowingDialogs = false
                shownCardIds.clear()
                currentCardIndex = -1
                showPopupNotification()
                return
            }

            // If we don't have a current dialog or we've shown all pairs in the current dialog
            if (currentDialogIndex == -1 || currentDialogPairIndex == -1 || 
                (currentDialogIndex < dialogs.size && currentDialogPairIndex >= dialogs[currentDialogIndex].parseDialogPairs().size)) {
                
                // If we've shown all pairs in the current dialog, move to next dialog
                if (currentDialogIndex != -1 && currentDialogPairIndex >= dialogs[currentDialogIndex].parseDialogPairs().size) {
                    currentDialogIndex++
                    currentDialogPairIndex = 0
                }
                
                // If we've shown all dialogs, start over
                if (currentDialogIndex >= dialogs.size) {
                    android.util.Log.d("PopupService", "Shown all dialogs, starting over")
                    currentDialogIndex = 0
                    shownDialogIds.clear()
                }
                
                val dialog = if (settings.useRandomCards) {
                    val remainingDialogs = dialogs.filter { it.id !in shownDialogIds }
                    android.util.Log.d("PopupService", "Random Mode - Remaining Dialogs: ${remainingDialogs.size}")
                    if (remainingDialogs.isEmpty()) {
                        android.util.Log.d("PopupService", "No remaining dialogs, starting over")
                        shownDialogIds.clear()
                        dialogs.random()
                    } else {
                        remainingDialogs.random()
                    }
                } else {
                    if (currentDialogIndex == -1) {
                        currentDialogIndex = settings.startDialog  // Start from the selected dialog (no -1 needed here)
                    }
                    
                    if (currentDialogIndex >= dialogs.size) {
                        currentDialogIndex = 0
                    }
                    
                    dialogs[currentDialogIndex]
                }

                if (settings.useRandomCards) {
                    shownDialogIds.add(dialog.id)
                }
                
                currentDialogPairIndex = 0
                showDialogPairNotification(dialog)
            } else {
                // Continue showing pairs from current dialog
                val currentDialog = dialogs[currentDialogIndex]
                showDialogPairNotification(currentDialog)
            }
        }
    }

    private suspend fun showDialogPairNotification(dialog: Dialog) {
        val settings = preferencesManager.getPopupSettings() // Get settings
        val dialogPairs = dialog.parseDialogPairs()
        if (dialogPairs.isEmpty()) {
            android.util.Log.d("PopupService", "No dialog pairs found, moving to next dialog")
            currentDialogIndex++
            currentDialogPairIndex = -1
            showPopupNotification()
            return
        }

        if (currentDialogPairIndex >= dialogPairs.size) {
            android.util.Log.d("PopupService", "Reached end of dialog pairs, moving to next dialog")
            currentDialogIndex++
            currentDialogPairIndex = -1
            showPopupNotification()
            return
        }

        val currentPair = dialogPairs[currentDialogPairIndex]
        android.util.Log.d("PopupService", "Showing dialog pair ${currentDialogPairIndex + 1}/${dialogPairs.size}")

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cards)
            .setContentTitle("German Dialog")
            .setContentText("${currentPair.germanQuestion}\n${currentPair.germanAnswer}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Q: ${currentPair.germanQuestion}\nA: ${currentPair.germanAnswer}\n\nTranslation:\nQ: ${currentPair.englishQuestion}\nA: ${currentPair.englishAnswer}"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)  // This will remove the notification when tapped
            .setContentIntent(pendingIntent)
            .setGroup(WEAR_GROUP_KEY)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // If we have too many notifications, remove the oldest ones
        val activeNotifications = notificationManager.activeNotifications
        if (activeNotifications.size >= MAX_ACTIVE_NOTIFICATIONS) {
            // Sort by post time and keep only the most recent ones
            val oldestNotifications = activeNotifications
                .sortedBy { it.postTime }
                .take(activeNotifications.size - MAX_ACTIVE_NOTIFICATIONS + 1)
            
            oldestNotifications.forEach { notification ->
                notificationManager.cancel(notification.id)
            }
        }
        
        // Use timestamp-based ID
        val uniqueId = System.currentTimeMillis().toInt()
        notificationManager.notify(uniqueId, notification)
        
        currentDialogPairIndex++
    }

    private suspend fun showDialogNotification(dialog: Dialog) {
        // This method is now replaced by showDialogPairNotification
        showDialogPairNotification(dialog)
    }

    private fun showCardNotification(card: FlashCard) {
        val settings = preferencesManager.getPopupSettings()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(card.germanText)
            .setContentText(card.englishText)
            .setSmallIcon(R.drawable.ic_cards)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)  // This will remove the notification when tapped
            .setGroup(WEAR_GROUP_KEY)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // If we have too many notifications, remove the oldest ones
        val activeNotifications = notificationManager.activeNotifications
        if (activeNotifications.size >= MAX_ACTIVE_NOTIFICATIONS) {
            // Sort by post time and keep only the most recent ones
            val oldestNotifications = activeNotifications
                .sortedBy { it.postTime }
                .take(activeNotifications.size - MAX_ACTIVE_NOTIFICATIONS + 1)
            
            oldestNotifications.forEach { notification ->
                notificationManager.cancel(notification.id)
            }
        }
        
        // Use timestamp-based ID
        val uniqueId = System.currentTimeMillis().toInt()
        notificationManager.notify(uniqueId, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        notificationJob?.cancel()
        serviceScope.cancel()
        
        // Restart service if it was killed
        val intent = Intent(this, PopupNotificationService::class.java).apply {
            action = "START_POPUP_SERVICE"
        }
        startService(intent)
    }
}
