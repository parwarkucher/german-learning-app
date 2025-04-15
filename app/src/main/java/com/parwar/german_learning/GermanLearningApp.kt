package com.parwar.german_learning

import android.app.Application
import com.parwar.german_learning.media.MediaManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GermanLearningApp : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    @Inject
    lateinit var mediaManager: MediaManager

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            mediaManager.startService()
        }
    }

    override fun onTerminate() {
        mediaManager.stopService()
        super.onTerminate()
    }
}
