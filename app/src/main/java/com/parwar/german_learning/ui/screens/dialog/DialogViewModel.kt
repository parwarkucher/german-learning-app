package com.parwar.german_learning.ui.screens.dialog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parwar.german_learning.data.AppDatabase
import com.parwar.german_learning.data.models.Dialog
import com.parwar.german_learning.media.MediaManager
import com.parwar.german_learning.utils.TranslationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DialogViewModel @Inject constructor(
    private val database: AppDatabase,
    val mediaManager: MediaManager,
    val translationManager: TranslationManager
) : ViewModel() {

    val dialogs = database.dialogDao().getAllDialogs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addDialog(dialog: Dialog) {
        viewModelScope.launch {
            database.dialogDao().insertDialog(dialog)
        }
    }

    fun updateDialog(dialog: Dialog) {
        viewModelScope.launch {
            database.dialogDao().updateDialog(dialog)
        }
    }

    fun deleteDialog(dialog: Dialog) {
        viewModelScope.launch {
            database.dialogDao().deleteDialog(dialog)
        }
    }
}
