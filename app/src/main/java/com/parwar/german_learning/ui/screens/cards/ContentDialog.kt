package com.parwar.german_learning.ui.screens.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.parwar.german_learning.R
import com.parwar.german_learning.data.models.ContentType
import com.parwar.german_learning.data.models.FlashCard
import com.parwar.german_learning.media.MediaManager
import com.parwar.german_learning.utils.TranslationManager
import androidx.compose.runtime.DisposableEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentDialog(
    onDismiss: () -> Unit,
    onSave: (FlashCard) -> Unit,
    initialFlashCard: FlashCard? = null,
    mediaManager: MediaManager,
    translationManager: TranslationManager
) {
    var selectedType by remember { mutableStateOf(initialFlashCard?.type ?: ContentType.WORD) }
    var germanText by remember { mutableStateOf(initialFlashCard?.germanText ?: "") }
    var englishText by remember { mutableStateOf(initialFlashCard?.englishText ?: "") }
    var phonetic by remember { mutableStateOf(initialFlashCard?.phonetic ?: "") }
    var tags by remember { mutableStateOf(initialFlashCard?.tags?.joinToString(",") ?: "") }
    var examples by remember { mutableStateOf(initialFlashCard?.examples?.joinToString("\n") ?: "") }
    var grammarNotes by remember { mutableStateOf(initialFlashCard?.grammarNotes ?: "") }
    var contextNotes by remember { mutableStateOf(initialFlashCard?.contextNotes ?: "") }
    var category by remember { mutableStateOf(initialFlashCard?.category ?: "") }
    var relatedWords by remember { mutableStateOf(initialFlashCard?.relatedWords?.joinToString(",") ?: "") }

    var showTypeMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialFlashCard == null) "Add New Content" else "Edit Content") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Content Type Selection
                ExposedDropdownMenuBox(
                    expanded = showTypeMenu,
                    onExpandedChange = { showTypeMenu = it },
                ) {
                    TextField(
                        value = selectedType.name.replace("_", " "),
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Content Type") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = showTypeMenu,
                        onDismissRequest = { showTypeMenu = false }
                    ) {
                        ContentType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name.replace("_", " ")) },
                                onClick = {
                                    selectedType = type
                                    showTypeMenu = false
                                }
                            )
                        }
                    }
                }

                // Type-specific fields
                when (selectedType) {
                    ContentType.BATCH_CARDS -> {
                        var batchText by remember { mutableStateOf("") }
                        var parsedCards by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
                        var showAllCards by remember { mutableStateOf(false) }

                        OutlinedTextField(
                            value = batchText,
                            onValueChange = { text ->
                                batchText = text
                                // Parse the input text into pairs of German-English
                                parsedCards = text.split("\n")
                                    .asSequence()
                                    .filter { it.trim().isNotEmpty() }
                                    .chunked(2)
                                    .filter { it.size == 2 }
                                    .map { Pair(it[0].trim(), it[1].trim()) }
                                    .toList()
                            },
                            label = { Text("Batch Cards (German text with English translation below)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 10
                        )

                        if (parsedCards.isNotEmpty()) {
                            Text(
                                "Preview (${parsedCards.size} cards):",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                val cardsToShow = if (showAllCards) parsedCards else parsedCards.take(5)
                                cardsToShow.forEach { (german, english) ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp)
                                        ) {
                                            Text(german, style = MaterialTheme.typography.bodyLarge)
                                            Text(english, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                if (!showAllCards && parsedCards.size > 5) {
                                    TextButton(
                                        onClick = { showAllCards = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("... and ${parsedCards.size - 5} more cards (tap to show all)")
                                    }
                                } else if (showAllCards && parsedCards.size > 5) {
                                    TextButton(
                                        onClick = { showAllCards = false },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Show less")
                                    }
                                }
                            }
                        }

                        // Store the parsed cards for saving
                        DisposableEffect(parsedCards) {
                            germanText = batchText
                            onDispose { }
                        }
                    }
                    else -> {
                        // Common fields
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = germanText,
                                onValueChange = { germanText = it },
                                label = { Text("German Text") },
                                modifier = Modifier.weight(1f)
                            )
                            
                            Column(
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                IconButton(
                                    onClick = { mediaManager.speakGerman(germanText) }
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_volume_up),
                                        contentDescription = "Read German Text"
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = englishText,
                                onValueChange = { englishText = it },
                                label = { Text("English Translation") },
                                modifier = Modifier.weight(1f)
                            )

                            Column(
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                IconButton(
                                    onClick = { translationManager.translateWithDeviceFeature(germanText) }
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_translate),
                                        contentDescription = "Translate German Text"
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = phonetic,
                            onValueChange = { phonetic = it },
                            label = { Text("Phonetic Pronunciation") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = tags,
                            onValueChange = { tags = it },
                            label = { Text("Tags (comma separated)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = examples,
                            onValueChange = { examples = it },
                            label = { Text("Example Sentences (one per line)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )

                        // Type-specific fields
                        when (selectedType) {
                            ContentType.GRAMMAR_RULE -> {
                                OutlinedTextField(
                                    value = grammarNotes,
                                    onValueChange = { grammarNotes = it },
                                    label = { Text("Grammar Notes") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2
                                )
                            }
                            ContentType.CULTURAL_NOTE -> {
                                OutlinedTextField(
                                    value = contextNotes,
                                    onValueChange = { contextNotes = it },
                                    label = { Text("Cultural Context") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2
                                )
                            }
                            ContentType.WORD, ContentType.PHRASE -> {
                                OutlinedTextField(
                                    value = relatedWords,
                                    onValueChange = { relatedWords = it },
                                    label = { Text("Related Words (comma separated)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            else -> {}
                        }

                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedType == ContentType.BATCH_CARDS) {
                        // Handle batch cards
                        val cards = germanText.split("\n")
                            .asSequence()
                            .filter { it.trim().isNotEmpty() }
                            .chunked(2)
                            .filter { it.size == 2 }
                            .map { (german, english) ->
                                FlashCard(
                                    id = 0,
                                    type = ContentType.WORD,
                                    germanText = german.trim(),
                                    englishText = english.trim(),
                                    phonetic = "",
                                    tags = emptyList(),
                                    examples = emptyList(),
                                    category = null
                                )
                            }
                            .toList()
                        cards.forEach { onSave(it) }
                        onDismiss()
                    } else {
                        // Handle single card
                        val flashcard = FlashCard(
                            id = initialFlashCard?.id ?: 0, // Keep the same ID if editing
                            type = selectedType,
                            germanText = germanText,
                            englishText = englishText,
                            phonetic = phonetic,
                            tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            examples = examples.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                            grammarNotes = grammarNotes.takeIf { it.isNotEmpty() },
                            contextNotes = contextNotes.takeIf { it.isNotEmpty() },
                            category = category.takeIf { it.isNotEmpty() },
                            relatedWords = relatedWords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        )
                        onSave(flashcard)
                        onDismiss()
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
