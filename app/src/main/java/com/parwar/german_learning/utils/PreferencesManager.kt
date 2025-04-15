package com.parwar.german_learning.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import com.parwar.german_learning.data.models.PopupSettings

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "german_learning_preferences")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val internalPrefs: SharedPreferences = 
        context.getSharedPreferences("german_learning_prefs", Context.MODE_PRIVATE)
    private var externalPrefs: JSONObject? = null
    private var prefsFile: File? = null
    private var isExternalStorageAvailable = false

    companion object {
        private const val PREFERENCES_NAME = "german_learning_preferences"
        const val KEY_LAST_PLAYED_INDEX = "last_played_index"
        const val KEY_CURRENT_INDEX = "current_index"
        const val KEY_SPEECH_RATE = "speech_rate"
        const val KEY_REPETITIONS = "repetitions_per_card"
        const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
        const val KEY_SELECTED_MODEL = "selected_model"
        
        // Popup Settings Keys
        const val KEY_POPUP_ENABLED = "popup_enabled"
        const val KEY_POPUP_FREQUENCY = "popup_frequency"
        const val KEY_POPUP_START_TIME = "popup_start_time"
        const val KEY_POPUP_END_TIME = "popup_end_time"
        const val KEY_POPUP_SHOW_IN_APPS = "popup_show_in_apps"
        const val KEY_POPUP_DURATION = "popup_duration"
        const val KEY_POPUP_USE_RANDOM = "popup_use_random"
        const val KEY_POPUP_START_CARD = "popup_start_card"
        const val KEY_POPUP_SWITCH_TO_DIALOGS = "popup_switch_to_dialogs"
        const val KEY_POPUP_START_DIALOG = "popup_start_dialog"
    }

    fun initializeExternalStorage() {
        try {
            if (!isExternalStorageWritable()) {
                isExternalStorageAvailable = false
                return
            }

            val folder = File(Environment.getExternalStorageDirectory(), "GermanLearning")
            if (!folder.exists() && !folder.mkdirs()) {
                isExternalStorageAvailable = false
                return
            }

            prefsFile = File(folder, "preferences.json")
            if (!prefsFile!!.exists()) {
                // If external preferences don't exist, create them from internal preferences
                val json = JSONObject()
                // Copy all internal preferences to external
                internalPrefs.all.forEach { (key, value) ->
                    when (value) {
                        is String -> json.put(key, value)
                        is Int -> json.put(key, value)
                        is Long -> json.put(key, value)
                        is Float -> json.put(key, value)
                        is Boolean -> json.put(key, value)
                    }
                }
                prefsFile!!.writeText(json.toString())
            }

            externalPrefs = JSONObject(prefsFile!!.readText())
            isExternalStorageAvailable = true

        } catch (e: Exception) {
            e.printStackTrace()
            isExternalStorageAvailable = false
        }
    }

    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    private fun migrateToExternal() {
        try {
            externalPrefs?.put(KEY_LAST_PLAYED_INDEX, internalPrefs.getInt(KEY_LAST_PLAYED_INDEX, 0))
            externalPrefs?.put(KEY_CURRENT_INDEX, internalPrefs.getInt(KEY_CURRENT_INDEX, 0))
            externalPrefs?.put(KEY_SPEECH_RATE, internalPrefs.getFloat(KEY_SPEECH_RATE, 0.8f))
            externalPrefs?.put(KEY_REPETITIONS, internalPrefs.getInt(KEY_REPETITIONS, 1))
            
            // Migrate popup settings
            externalPrefs?.put(KEY_POPUP_ENABLED, internalPrefs.getBoolean(KEY_POPUP_ENABLED, false))
            externalPrefs?.put(KEY_POPUP_FREQUENCY, internalPrefs.getInt(KEY_POPUP_FREQUENCY, 60))
            externalPrefs?.put(KEY_POPUP_START_TIME, internalPrefs.getInt(KEY_POPUP_START_TIME, 9))
            externalPrefs?.put(KEY_POPUP_END_TIME, internalPrefs.getInt(KEY_POPUP_END_TIME, 21))
            externalPrefs?.put(KEY_POPUP_SHOW_IN_APPS, internalPrefs.getBoolean(KEY_POPUP_SHOW_IN_APPS, true))
            externalPrefs?.put(KEY_POPUP_DURATION, internalPrefs.getInt(KEY_POPUP_DURATION, 10))
            externalPrefs?.put(KEY_POPUP_USE_RANDOM, internalPrefs.getBoolean(KEY_POPUP_USE_RANDOM, true))
            externalPrefs?.put(KEY_POPUP_START_CARD, internalPrefs.getInt(KEY_POPUP_START_CARD, 0))
            externalPrefs?.put(KEY_POPUP_SWITCH_TO_DIALOGS, internalPrefs.getBoolean(KEY_POPUP_SWITCH_TO_DIALOGS, false))
            externalPrefs?.put(KEY_POPUP_START_DIALOG, internalPrefs.getInt(KEY_POPUP_START_DIALOG, 0))
            
            savePrefs()
        } catch (e: Exception) {
            isExternalStorageAvailable = false
            externalPrefs = null
        }
    }

    private fun savePrefs() {
        if (!isExternalStorageAvailable) return
        
        try {
            externalPrefs?.let { prefs ->
                prefsFile?.writeText(prefs.toString())
            }
        } catch (e: Exception) {
            isExternalStorageAvailable = false
            externalPrefs = null
        }
    }

    var lastPlayedIndex: Int
        get() = if (isExternalStorageAvailable) {
            externalPrefs?.optInt(KEY_LAST_PLAYED_INDEX, 0) ?: internalPrefs.getInt(KEY_LAST_PLAYED_INDEX, 0)
        } else {
            internalPrefs.getInt(KEY_LAST_PLAYED_INDEX, 0)
        }
        set(value) {
            if (isExternalStorageAvailable && externalPrefs != null) {
                try {
                    externalPrefs?.put(KEY_LAST_PLAYED_INDEX, value)
                    savePrefs()
                } catch (e: Exception) {
                    internalPrefs.edit().putInt(KEY_LAST_PLAYED_INDEX, value).apply()
                }
            } else {
                internalPrefs.edit().putInt(KEY_LAST_PLAYED_INDEX, value).apply()
            }
        }

    var currentIndex: Int
        get() = if (isExternalStorageAvailable) {
            externalPrefs?.optInt(KEY_CURRENT_INDEX, 0) ?: internalPrefs.getInt(KEY_CURRENT_INDEX, 0)
        } else {
            internalPrefs.getInt(KEY_CURRENT_INDEX, 0)
        }
        set(value) {
            if (isExternalStorageAvailable && externalPrefs != null) {
                try {
                    externalPrefs?.put(KEY_CURRENT_INDEX, value)
                    savePrefs()
                } catch (e: Exception) {
                    internalPrefs.edit().putInt(KEY_CURRENT_INDEX, value).apply()
                }
            } else {
                internalPrefs.edit().putInt(KEY_CURRENT_INDEX, value).apply()
            }
        }

    var speechRate: Float
        get() = if (isExternalStorageAvailable) {
            externalPrefs?.optDouble(KEY_SPEECH_RATE, 0.8)?.toFloat() 
                ?: internalPrefs.getFloat(KEY_SPEECH_RATE, 0.8f)
        } else {
            internalPrefs.getFloat(KEY_SPEECH_RATE, 0.8f)
        }
        set(value) {
            if (isExternalStorageAvailable && externalPrefs != null) {
                try {
                    externalPrefs?.put(KEY_SPEECH_RATE, value.toDouble())
                    savePrefs()
                } catch (e: Exception) {
                    internalPrefs.edit().putFloat(KEY_SPEECH_RATE, value).apply()
                }
            } else {
                internalPrefs.edit().putFloat(KEY_SPEECH_RATE, value).apply()
            }
        }

    var repetitionsPerCard: Int
        get() = if (isExternalStorageAvailable) {
            externalPrefs?.optInt(KEY_REPETITIONS, 1) ?: internalPrefs.getInt(KEY_REPETITIONS, 1)
        } else {
            internalPrefs.getInt(KEY_REPETITIONS, 1)
        }
        set(value) {
            if (isExternalStorageAvailable && externalPrefs != null) {
                try {
                    externalPrefs?.put(KEY_REPETITIONS, value)
                    savePrefs()
                } catch (e: Exception) {
                    internalPrefs.edit().putInt(KEY_REPETITIONS, value).apply()
                }
            } else {
                internalPrefs.edit().putInt(KEY_REPETITIONS, value).apply()
            }
        }

    suspend fun saveOpenRouterApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey(KEY_OPENROUTER_API_KEY)] = apiKey
        }
    }

    suspend fun getOpenRouterApiKey(): String {
        return context.dataStore.data.map { preferences ->
            preferences[stringPreferencesKey(KEY_OPENROUTER_API_KEY)] ?: ""
        }.first()
    }

    suspend fun saveSelectedModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey(KEY_SELECTED_MODEL)] = model
        }
    }

    suspend fun getSelectedModel(): String {
        return context.dataStore.data.map { preferences ->
            preferences[stringPreferencesKey(KEY_SELECTED_MODEL)] ?: "gpt-3.5-turbo"
        }.first()
    }

    fun getPopupSettings(): PopupSettings {
        return if (isExternalStorageAvailable) {
            PopupSettings(
                isEnabled = externalPrefs?.optBoolean(KEY_POPUP_ENABLED, false) 
                    ?: internalPrefs.getBoolean(KEY_POPUP_ENABLED, false),
                frequency = externalPrefs?.optInt(KEY_POPUP_FREQUENCY, 60)
                    ?: internalPrefs.getInt(KEY_POPUP_FREQUENCY, 60),
                startTime = externalPrefs?.optInt(KEY_POPUP_START_TIME, 9)
                    ?: internalPrefs.getInt(KEY_POPUP_START_TIME, 9),
                endTime = externalPrefs?.optInt(KEY_POPUP_END_TIME, 21)
                    ?: internalPrefs.getInt(KEY_POPUP_END_TIME, 21),
                showInApps = externalPrefs?.optBoolean(KEY_POPUP_SHOW_IN_APPS, true)
                    ?: internalPrefs.getBoolean(KEY_POPUP_SHOW_IN_APPS, true),
                durationSeconds = externalPrefs?.optInt(KEY_POPUP_DURATION, 10)
                    ?: internalPrefs.getInt(KEY_POPUP_DURATION, 10),
                useRandomCards = externalPrefs?.optBoolean(KEY_POPUP_USE_RANDOM, true)
                    ?: internalPrefs.getBoolean(KEY_POPUP_USE_RANDOM, true),
                startCard = externalPrefs?.optInt(KEY_POPUP_START_CARD, 0)
                    ?: internalPrefs.getInt(KEY_POPUP_START_CARD, 0),
                switchToDialogsAfterCards = externalPrefs?.optBoolean(KEY_POPUP_SWITCH_TO_DIALOGS, false)
                    ?: internalPrefs.getBoolean(KEY_POPUP_SWITCH_TO_DIALOGS, false),
                startDialog = externalPrefs?.optInt(KEY_POPUP_START_DIALOG, 0)
                    ?: internalPrefs.getInt(KEY_POPUP_START_DIALOG, 0)
            )
        } else {
            PopupSettings(
                isEnabled = internalPrefs.getBoolean(KEY_POPUP_ENABLED, false),
                frequency = internalPrefs.getInt(KEY_POPUP_FREQUENCY, 60),
                startTime = internalPrefs.getInt(KEY_POPUP_START_TIME, 9),
                endTime = internalPrefs.getInt(KEY_POPUP_END_TIME, 21),
                showInApps = internalPrefs.getBoolean(KEY_POPUP_SHOW_IN_APPS, true),
                durationSeconds = internalPrefs.getInt(KEY_POPUP_DURATION, 10),
                useRandomCards = internalPrefs.getBoolean(KEY_POPUP_USE_RANDOM, true),
                startCard = internalPrefs.getInt(KEY_POPUP_START_CARD, 0),
                switchToDialogsAfterCards = internalPrefs.getBoolean(KEY_POPUP_SWITCH_TO_DIALOGS, false),
                startDialog = internalPrefs.getInt(KEY_POPUP_START_DIALOG, 0)
            )
        }
    }

    fun savePopupSettings(settings: PopupSettings) {
        if (isExternalStorageAvailable && externalPrefs != null) {
            try {
                externalPrefs?.put(KEY_POPUP_ENABLED, settings.isEnabled)
                externalPrefs?.put(KEY_POPUP_FREQUENCY, settings.frequency)
                externalPrefs?.put(KEY_POPUP_START_TIME, settings.startTime)
                externalPrefs?.put(KEY_POPUP_END_TIME, settings.endTime)
                externalPrefs?.put(KEY_POPUP_SHOW_IN_APPS, settings.showInApps)
                externalPrefs?.put(KEY_POPUP_DURATION, settings.durationSeconds)
                externalPrefs?.put(KEY_POPUP_USE_RANDOM, settings.useRandomCards)
                externalPrefs?.put(KEY_POPUP_START_CARD, settings.startCard)
                externalPrefs?.put(KEY_POPUP_SWITCH_TO_DIALOGS, settings.switchToDialogsAfterCards)
                externalPrefs?.put(KEY_POPUP_START_DIALOG, settings.startDialog)
                savePrefs()
            } catch (e: Exception) {
                savePopupSettingsInternal(settings)
            }
        } else {
            savePopupSettingsInternal(settings)
        }
    }

    private fun savePopupSettingsInternal(settings: PopupSettings) {
        internalPrefs.edit().apply {
            putBoolean(KEY_POPUP_ENABLED, settings.isEnabled)
            putInt(KEY_POPUP_FREQUENCY, settings.frequency)
            putInt(KEY_POPUP_START_TIME, settings.startTime)
            putInt(KEY_POPUP_END_TIME, settings.endTime)
            putBoolean(KEY_POPUP_SHOW_IN_APPS, settings.showInApps)
            putInt(KEY_POPUP_DURATION, settings.durationSeconds)
            putBoolean(KEY_POPUP_USE_RANDOM, settings.useRandomCards)
            putInt(KEY_POPUP_START_CARD, settings.startCard)
            putBoolean(KEY_POPUP_SWITCH_TO_DIALOGS, settings.switchToDialogsAfterCards)
            putInt(KEY_POPUP_START_DIALOG, settings.startDialog)
            apply()
        }
    }
}
