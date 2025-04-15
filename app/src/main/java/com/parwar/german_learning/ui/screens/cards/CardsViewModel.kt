package com.parwar.german_learning.ui.screens.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parwar.german_learning.data.dao.FlashCardDao
import com.parwar.german_learning.data.models.FlashCard
import com.parwar.german_learning.media.MediaManager
import com.parwar.german_learning.utils.TranslationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CardsViewModel @Inject constructor(
    private val flashCardDao: FlashCardDao,
    val mediaManager: MediaManager,
    val translationManager: TranslationManager
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    private val _flashcards = MutableStateFlow<List<FlashCard>>(emptyList())
    private val _originalCards = MutableStateFlow<List<FlashCard>>(emptyList())

    val searchQuery = _searchQuery.asStateFlow()
    val flashcards = combine(_flashcards, _searchQuery) { cards, query ->
        if (query.isBlank()) {
            cards.mapIndexed { index, card -> 
                Pair(index + 1, card)
            }
        } else {
            val originalPositions = _originalCards.value.withIndex()
                .associate { (index, card) -> card to (index + 1) }
            
            cards.filter { card ->
                card.germanText.contains(query, ignoreCase = true) ||
                card.englishText.contains(query, ignoreCase = true) ||
                card.tags.any { it.contains(query, ignoreCase = true) } ||
                card.examples.any { it.contains(query, ignoreCase = true) }
            }.map { card ->
                Pair(originalPositions[card] ?: 0, card)
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    init {
        loadFlashcards()
    }

    private fun loadFlashcards() {
        viewModelScope.launch {
            flashCardDao.getAllFlashCards().collect { cards ->
                _flashcards.value = cards
                _originalCards.value = cards
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addFlashCard(flashCard: FlashCard) {
        viewModelScope.launch {
            flashCardDao.insertFlashCard(flashCard)
        }
    }

    fun updateFlashCard(flashCard: FlashCard) {
        viewModelScope.launch {
            flashCardDao.updateFlashCard(flashCard)
        }
    }

    fun deleteFlashCard(flashCard: FlashCard) {
        viewModelScope.launch {
            flashCardDao.deleteFlashCard(flashCard)
        }
    }
}
