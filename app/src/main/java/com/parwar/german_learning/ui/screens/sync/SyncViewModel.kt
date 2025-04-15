package com.parwar.german_learning.ui.screens.sync

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parwar.german_learning.services.GoogleDriveService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val googleDriveService: GoogleDriveService,
    private val application: Application
) : ViewModel() {
    companion object {
        private const val TAG = "SyncViewModel"
    }

    private val _syncMessages = MutableStateFlow<List<String>>(emptyList())
    val syncMessages: StateFlow<List<String>> = _syncMessages.asStateFlow()

    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _autoSync = MutableStateFlow(false)
    val autoSync: StateFlow<Boolean> = _autoSync.asStateFlow()

    private val _lastSyncTime = MutableStateFlow("Never")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState = _syncState.asStateFlow()

    init {
        Log.d(TAG, "Initializing SyncViewModel")
        checkSignInStatus()
    }

    private fun checkSignInStatus() {
        _isSignedIn.value = googleDriveService.isSignedIn()
        Log.d(TAG, "Sign-in status: ${_isSignedIn.value}")
    }

    fun getSignInIntent(): Intent {
        Log.d(TAG, "Getting sign-in intent")
        return googleDriveService.getSignInIntent()
    }

    fun onSignInSuccess() {
        Log.d(TAG, "Sign-in successful")
        _isSignedIn.value = true
        viewModelScope.launch {
            startSync() // Automatically sync after successful sign-in
        }
    }

    fun onSignInError(message: String) {
        Log.e(TAG, "Sign-in error: $message")
        _isSignedIn.value = false
        addMessage("Sign in error: $message")
    }

    fun signOut() {
        Log.d(TAG, "Signing out")
        viewModelScope.launch {
            googleDriveService.getSignInClient().signOut().addOnCompleteListener {
                Log.d(TAG, "Sign-out completed")
                _isSignedIn.value = false
                _autoSync.value = false
                _syncMessages.value = emptyList()
                checkSignInStatus()
            }
        }
    }

    fun startSync() {
        Log.d(TAG, "Starting sync")
        viewModelScope.launch {
            _syncMessages.value = emptyList()
            _syncState.value = SyncState.Loading
            googleDriveService.sync()
                .catch { e -> 
                    Log.e(TAG, "Sync error", e)
                    addMessage("Error: ${e.message}")
                    _syncState.value = SyncState.Error(e)
                }
                .collect { message ->
                    Log.d(TAG, "Sync message: $message")
                    addMessage(message)
                    if (message == "Sync completed successfully") {
                        updateLastSyncTime()
                        _syncState.value = SyncState.Success
                        delay(2000)
                        
                        // Force restart the app
                        val intent = application.packageManager.getLaunchIntentForPackage(application.packageName)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        application.startActivity(intent)
                        
                        // Kill the current process
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
        }
    }

    private fun addMessage(message: String) {
        Log.d(TAG, "Adding message: $message")
        _syncMessages.value = _syncMessages.value + message
    }

    private fun updateLastSyncTime() {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val newTime = LocalDateTime.now().format(formatter)
        Log.d(TAG, "Updating last sync time to: $newTime")
        _lastSyncTime.value = newTime
    }

    fun setAutoSync(enabled: Boolean) {
        Log.d(TAG, "Setting auto-sync to: $enabled")
        _autoSync.value = enabled
    }
}

sealed class SyncState {
    object Idle : SyncState()
    object Loading : SyncState()
    object Success : SyncState()
    data class Error(val error: Throwable) : SyncState()
}
