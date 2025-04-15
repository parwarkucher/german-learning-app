package com.parwar.german_learning.utils

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun translateWithDeviceFeature(text: String) {
        // Create an intent to open Samsung's translation feature
        val intent = Intent().apply {
            action = Intent.ACTION_PROCESS_TEXT
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT, text)
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Start the activity
        context.startActivity(intent)
    }
}
