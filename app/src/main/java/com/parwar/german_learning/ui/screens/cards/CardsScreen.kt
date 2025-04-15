package com.parwar.german_learning.ui.screens.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.parwar.german_learning.data.models.FlashCard
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    navController: NavController,
    viewModel: CardsViewModel = hiltViewModel()
) {
    var showDialog by remember { mutableStateOf(false) }
    var selectedFlashCard by remember { mutableStateOf<FlashCard?>(null) }
    val flashcards by viewModel.flashcards.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var scrollbarHeight by remember { mutableStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                selectedFlashCard = null
                showDialog = true 
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Content")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search words, phrases, or tags...") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(flashcards) { (originalIndex, flashcard) ->
                        FlashCardItem(
                            index = originalIndex,
                            flashcard = flashcard,
                            onDelete = { viewModel.deleteFlashCard(it) },
                            onEdit = { 
                                selectedFlashCard = it
                                showDialog = true
                            }
                        )
                    }
                }

                // Fast scroll bar
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(20.dp)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                        .onSizeChanged { size ->
                            scrollbarHeight = size.height
                        }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                if (flashcards.isNotEmpty() && scrollbarHeight > 0) {
                                    val maxOffset = scrollbarHeight - 48f // thumb height
                                    dragOffset = (dragOffset + delta).coerceIn(0f, maxOffset)
                                    val percentage = dragOffset / maxOffset
                                    val targetIndex = (percentage * (flashcards.size - 1)).toInt()
                                    coroutineScope.launch {
                                        listState.scrollToItem(targetIndex)
                                    }
                                }
                            },
                            onDragStarted = { 
                                isDragging = true
                                if (flashcards.isNotEmpty() && scrollbarHeight > 0) {
                                    val percentage = listState.firstVisibleItemIndex.toFloat() / (flashcards.size - 1)
                                    dragOffset = percentage * (scrollbarHeight - 48f)
                                }
                            },
                            onDragStopped = { isDragging = false }
                        )
                ) {
                    // Track
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    ) {}

                    // Thumb
                    val thumbOffset = if (isDragging) {
                        dragOffset
                    } else {
                        if (flashcards.isEmpty()) {
                            0f
                        } else {
                            val progress = listState.firstVisibleItemIndex.toFloat() / (flashcards.size - 1).coerceAtLeast(1)
                            progress * (scrollbarHeight - 48f)
                        }
                    }
                    
                    Surface(
                        modifier = Modifier
                            .width(20.dp)
                            .height(48.dp)
                            .offset(y = with(density) { thumbOffset.toDp() }),
                        color = MaterialTheme.colorScheme.primary.copy(
                            alpha = if (isDragging) 0.8f else 0.5f
                        )
                    ) {}
                }
            }
        }

        if (showDialog) {
            ContentDialog(
                onDismiss = { showDialog = false },
                onSave = { flashcard ->
                    if (selectedFlashCard == null) {
                        viewModel.addFlashCard(flashcard)
                    } else {
                        viewModel.updateFlashCard(flashcard)
                    }
                    showDialog = false
                },
                initialFlashCard = selectedFlashCard,
                mediaManager = viewModel.mediaManager,
                translationManager = viewModel.translationManager
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashCardItem(
    index: Int,
    flashcard: FlashCard,
    onDelete: (FlashCard) -> Unit,
    onEdit: (FlashCard) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "#$index",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                AssistChip(
                    onClick = { },
                    label = { Text(flashcard.type.name.replace("_", " ")) }
                )
            }
            
            Text(
                text = flashcard.germanText,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = flashcard.englishText,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = flashcard.phonetic,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    flashcard.tags.forEach { tag ->
                        AssistChip(
                            onClick = { },
                            label = { Text(tag) }
                        )
                    }
                }
                
                if (flashcard.examples.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Examples:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    flashcard.examples.forEach { example ->
                        Text(
                            text = "• $example",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                flashcard.grammarNotes?.let { notes ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Grammar Notes:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                flashcard.contextNotes?.let { notes ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Context:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (flashcard.relatedWords.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Related Words:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        flashcard.relatedWords.forEach { word ->
                            AssistChip(
                                onClick = { },
                                label = { Text(word) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onEdit(flashcard) }) {
                        Text("Edit")
                    }
                    TextButton(onClick = { showDeleteDialog = true }) {
                        Text("Delete")
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Content") },
            text = { Text("Are you sure you want to delete this item?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(flashcard)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
