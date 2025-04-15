package com.parwar.german_learning.ui.screens.gym

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.parwar.german_learning.R
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymScreen(
    navController: NavController,
    viewModel: GymViewModel = hiltViewModel()
) {
    val currentCard by viewModel.currentCard.collectAsState()
    val currentDialog by viewModel.currentDialog.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    val popupSettings by viewModel.popupSettings.collectAsState()
    val currentDialogPair by viewModel.currentDialogPair.collectAsState()
    var showStartDialog by remember { mutableStateOf(false) }
    var startFromCardText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Gym Mode",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Top buttons row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { showStartDialog = true }
            ) {
                Text("Playback Options")
            }

            Button(
                onClick = { navController.navigate("popup") }
            ) {
                Text(if (popupSettings.isEnabled) "Popup: ON" else "Popup: OFF")
            }
        }

        // Dialog Mode Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dialog Mode")
            Switch(
                checked = viewModel.dialogMode.value,
                onCheckedChange = { viewModel.setDialogMode(it) }
            )
        }

        // Current playing card or dialog
        if (viewModel.dialogMode.value) {
            currentDialog?.let { dialog ->
                if (!isPlaying) {
                    // Show full dialog preview only when not playing
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            dialog.parseDialogPairs().forEach { pair ->
                                // Question
                                Text(
                                    text = pair.germanQuestion,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    text = pair.englishQuestion,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                
                                // Answer
                                Text(
                                    text = pair.germanAnswer,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    text = pair.englishAnswer,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Show only current dialog pair during playback
                    currentDialogPair?.let { pair ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                // Question
                                Text(
                                    text = pair.germanQuestion,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    text = pair.englishQuestion,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                
                                // Answer
                                Text(
                                    text = pair.germanAnswer,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    text = pair.englishAnswer,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else {
            currentCard?.let { card ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = card.germanText,
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Text(
                            text = card.englishText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (card.examples.isNotEmpty()) {
                            Text(
                                text = card.examples.first(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            } ?: Text(
                text = "No cards available",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Playback controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.skipToPrevious() },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_previous),
                    contentDescription = "Previous",
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = { viewModel.togglePlayback() },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    ),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = { viewModel.skipToNext() },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_next),
                    contentDescription = "Next",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Progress indicator - only show in card mode
        if (!viewModel.dialogMode.value) {
            LinearProgressIndicator(
                progress = playbackProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )
        }

        // Settings
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Playback Settings",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "Speech Rate: ${(viewModel.speechRate.value * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = viewModel.speechRate.value,
                    onValueChange = { viewModel.setSpeechRate(it) },
                    valueRange = 0.5f..2f,
                    steps = 15
                )

                Text(
                    text = "Repetitions per Card: ${viewModel.repetitionsPerCard.value}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Slider(
                    value = viewModel.repetitionsPerCard.value.toFloat(),
                    onValueChange = { viewModel.setRepetitionsPerCard(it.roundToInt()) },
                    valueRange = 1f..5f,
                    steps = 4
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Repeat Mode")
                    Switch(
                        checked = viewModel.repeatMode.value,
                        onCheckedChange = { viewModel.setRepeatMode(it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Shuffle")
                    Switch(
                        checked = viewModel.shuffleMode.value,
                        onCheckedChange = { viewModel.setShuffleMode(it) }
                    )
                }
            }
        }
    }

    if (showStartDialog) {
        AlertDialog(
            onDismissRequest = { 
                showStartDialog = false
                startFromCardText = ""
            },
            title = { Text("Playback Options") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.resumeFromLast()
                            showStartDialog = false
                            startFromCardText = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Resume from Last (#${viewModel.lastPlayedIndex.value + 1})")
                    }
                    Button(
                        onClick = {
                            viewModel.startRandom()
                            showStartDialog = false
                            startFromCardText = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Random")
                    }
                    if (viewModel.dialogMode.value) {
                        OutlinedTextField(
                            value = startFromCardText,
                            onValueChange = { value ->
                                if (value.isEmpty() || value.all { it.isDigit() }) {
                                    startFromCardText = value
                                }
                            },
                            label = { Text("Start from Dialog #") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    startFromCardText.toIntOrNull()?.let { index ->
                                        if (index > 0) {
                                            viewModel.setDialogStartIndex(index - 1)
                                            showStartDialog = false
                                            startFromCardText = ""
                                        }
                                    }
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = startFromCardText,
                            onValueChange = { value ->
                                if (value.isEmpty() || value.all { it.isDigit() }) {
                                    startFromCardText = value
                                }
                            },
                            label = { Text("Start from Card #") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    startFromCardText.toIntOrNull()?.let { index ->
                                        if (index > 0) {
                                            viewModel.startFromCard(index - 1)
                                            showStartDialog = false
                                            startFromCardText = ""
                                        }
                                    }
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    showStartDialog = false
                    startFromCardText = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
