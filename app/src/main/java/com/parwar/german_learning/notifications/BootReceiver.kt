package com.parwar.german_learning.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.parwar.german_learning.utils.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settings = preferencesManager.getPopupSettings()
            if (settings.isEnabled) {
                val serviceIntent = Intent(context, PopupNotificationService::class.java).apply {
                    action = "START_POPUP_SERVICE"
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
