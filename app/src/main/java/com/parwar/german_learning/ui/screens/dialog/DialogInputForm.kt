package com.parwar.german_learning.ui.screens.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.parwar.german_learning.R
import com.parwar.german_learning.data.models.Dialog
import com.parwar.german_learning.media.MediaManager
import com.parwar.german_learning.utils.TranslationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogInputForm(
    onDismiss: () -> Unit,
    onSave: (Dialog) -> Unit,
    initialDialog: Dialog? = null,
    mediaManager: MediaManager,
    translationManager: TranslationManager
) {
    var germanText by remember { mutableStateOf(initialDialog?.germanText ?: "") }
    var englishText by remember { mutableStateOf(initialDialog?.englishText ?: "") }
    var participants by remember { mutableStateOf(initialDialog?.participants?.joinToString(",") ?: "") }
    var tags by remember { mutableStateOf(initialDialog?.tags?.joinToString(",") ?: "") }
    var category by remember { mutableStateOf(initialDialog?.category ?: "") }
    var difficulty by remember { mutableStateOf(initialDialog?.difficulty ?: "Beginner") }
    var contextNotes by remember { mutableStateOf(initialDialog?.contextNotes ?: "") }

    var showDifficultyMenu by remember { mutableStateOf(false) }
    val difficultyLevels = listOf("Beginner", "Intermediate", "Advanced")
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val dialog = Dialog(
                        id = initialDialog?.id ?: 0,
                        germanText = germanText.trim(),
                        englishText = englishText.trim(),
                        participants = participants.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        category = category.trim(),
                        difficulty = difficulty,
                        contextNotes = contextNotes.trim()
                    )
                    // Verify that the dialog can be parsed before saving
                    if (dialog.parseDialogPairs().isNotEmpty()) {
                        onSave(dialog)
                    }
                },
                enabled = germanText.isNotBlank() && 
                        englishText.isNotBlank() &&
                        Dialog(germanText = germanText, englishText = englishText)
                            .parseDialogPairs().isNotEmpty()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text(if (initialDialog == null) "Add New Dialog" else "Edit Dialog") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // German Text
                Column {
                    Text(
                        text = "Format: Q: [question]\nA: [answer]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = germanText,
                            onValueChange = { germanText = it },
                            label = { Text("German Dialog") },
                            modifier = Modifier.weight(1f),
                            minLines = 3,
                            placeholder = { Text("Q: Wie geht es dir?\nA: Mir geht es gut.") }
                        )
                        IconButton(
                            onClick = { mediaManager.speakGerman(germanText) }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_volume_up),
                                contentDescription = "Test German Audio"
                            )
                        }
                    }
                }

                // English Text
                Column {
                    Text(
                        text = "Format: Q: [question]\nA: [answer]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = englishText,
                            onValueChange = { englishText = it },
                            label = { Text("English Translation") },
                            modifier = Modifier.weight(1f),
                            minLines = 3,
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
                }

                // Preview Section
                if (germanText.isNotBlank() && englishText.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        val previewDialog = Dialog(
                            germanText = germanText,
                            englishText = englishText
                        )
                        val pairs = previewDialog.parseDialogPairs()
                        
                        if (pairs.isEmpty()) {
                            Text(
                                text = "No valid Q&A pairs found. Please check the format.",
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            pairs.forEachIndexed { index, pair ->
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
                                        Text("Pair ${index + 1}:", style = MaterialTheme.typography.titleSmall)
                                        Text("German Q: ${pair.germanQuestion}")
                                        Text("German A: ${pair.germanAnswer}")
                                        Text("English Q: ${pair.englishQuestion}")
                                        Text("English A: ${pair.englishAnswer}")
                                    }
                                }
                            }
                        }
                    }
                }

                // Participants
                OutlinedTextField(
                    value = participants,
                    onValueChange = { participants = it },
                    label = { Text("Participants (comma-separated)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Tags
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Category
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Difficulty Level
                ExposedDropdownMenuBox(
                    expanded = showDifficultyMenu,
                    onExpandedChange = { showDifficultyMenu = it }
                ) {
                    OutlinedTextField(
                        value = difficulty,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Difficulty Level") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showDifficultyMenu,
                        onDismissRequest = { showDifficultyMenu = false }
                    ) {
                        difficultyLevels.forEach { level ->
                            DropdownMenuItem(
                                text = { Text(level) },
                                onClick = {
                                    difficulty = level
                                    showDifficultyMenu = false
                                }
                            )
                        }
                    }
                }

                // Context Notes
                OutlinedTextField(
                    value = contextNotes,
                    onValueChange = { contextNotes = it },
                    label = { Text("Context Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        }
    )
}
