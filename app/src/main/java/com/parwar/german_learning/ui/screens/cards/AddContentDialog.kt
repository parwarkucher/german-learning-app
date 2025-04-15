package com.parwar.german_learning.ui.screens.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.parwar.german_learning.data.models.ContentType
import com.parwar.german_learning.data.models.FlashCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContentDialog(
    onDismiss: () -> Unit,
    onSave: (FlashCard) -> Unit
) {
    var selectedType by remember { mutableStateOf(ContentType.WORD) }
    var germanText by remember { mutableStateOf("") }
    var englishText by remember { mutableStateOf("") }
    var phonetic by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var examples by remember { mutableStateOf("") }
    var grammarNotes by remember { mutableStateOf("") }
    var contextNotes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var relatedWords by remember { mutableStateOf("") }

    var showTypeMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Content") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
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

                // Common fields
                OutlinedTextField(
                    value = germanText,
                    onValueChange = { germanText = it },
                    label = { Text("German Text") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = englishText,
                    onValueChange = { englishText = it },
                    label = { Text("English Translation") },
                    modifier = Modifier.fillMaxWidth()
                )

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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val flashcard = FlashCard(
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
