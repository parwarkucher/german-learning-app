package com.parwar.german_learning.ui.screens.popup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parwar.german_learning.data.dao.DialogDao
import com.parwar.german_learning.data.dao.FlashCardDao
import com.parwar.german_learning.data.models.PopupSettings
import com.parwar.german_learning.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PopupViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val flashCardDao: FlashCardDao,
    private val dialogDao: DialogDao
) : ViewModel() {
    private val _settings = MutableStateFlow(PopupSettings())
    val settings: StateFlow<PopupSettings> = _settings.asStateFlow()

    private val _totalCards = MutableStateFlow(0)
    val totalCards: StateFlow<Int> = _totalCards.asStateFlow()

    private val _totalDialogs = MutableStateFlow(0)
    val totalDialogs: StateFlow<Int> = _totalDialogs.asStateFlow()

    init {
        loadSettings()
        loadTotalCards()
        loadTotalDialogs()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _settings.value = preferencesManager.getPopupSettings()
        }
    }

    private fun loadTotalCards() {
        viewModelScope.launch {
            flashCardDao.getAllFlashCards().collect { cards ->
                _totalCards.value = cards.size
            }
        }
    }

    private fun loadTotalDialogs() {
        viewModelScope.launch {
            dialogDao.getAllDialogs().collect { dialogs ->
                _totalDialogs.value = dialogs.size
            }
        }
    }

    fun updateSettings(newSettings: PopupSettings) {
        viewModelScope.launch {
            preferencesManager.savePopupSettings(newSettings)
            _settings.value = newSettings
        }
    }
}
