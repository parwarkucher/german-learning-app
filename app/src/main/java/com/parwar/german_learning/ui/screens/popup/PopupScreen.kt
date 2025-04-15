package com.parwar.german_learning.ui.screens.popup

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.parwar.german_learning.data.models.PopupSettings
import com.parwar.german_learning.notifications.PopupNotificationService
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PopupScreen(
    viewModel: PopupViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val totalCards by viewModel.totalCards.collectAsState()
    val totalDialogs by viewModel.totalDialogs.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Popup Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        // Enable/Disable Popups with Service Control
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enable Popups")
            Switch(
                checked = settings.isEnabled,
                onCheckedChange = { isEnabled ->
                    viewModel.updateSettings(settings.copy(isEnabled = isEnabled))
                    val intent = Intent(context, PopupNotificationService::class.java)
                    intent.action = if (isEnabled) "START_POPUP_SERVICE" else "STOP_POPUP_SERVICE"
                    context.startService(intent)
                }
            )
        }

        // Card Selection Mode
        Column {
            Text("Card Selection Mode")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.useRandomCards,
                    onClick = {
                        viewModel.updateSettings(settings.copy(useRandomCards = true))
                    }
                )
                Text("Random Cards")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = !settings.useRandomCards,
                    onClick = {
                        viewModel.updateSettings(settings.copy(useRandomCards = false))
                    }
                )
                Text("Sequential from Start Card")
            }

            if (!settings.useRandomCards && totalCards > 0) {
                Column {
                    Text("Start from Card (1-$totalCards)")
                    Slider(
                        value = (settings.startCard + 1).toFloat(),
                        onValueChange = { value ->
                            viewModel.updateSettings(settings.copy(startCard = (value - 1).toInt()))
                        },
                        valueRange = 1f..totalCards.toFloat(),
                        steps = (totalCards - 1).coerceAtLeast(0)
                    )
                    Text("Starting from card ${settings.startCard + 1}")
                }
            }
        }

        // Frequency
        Column {
            Text("Frequency (minutes between popups)")
            Slider(
                value = settings.frequency.toFloat(),
                onValueChange = { value ->
                    viewModel.updateSettings(settings.copy(frequency = value.toInt()))
                },
                valueRange = 1f..240f,
                steps = 239
            )
            Text("${settings.frequency} minutes")
        }

        // Time Range
        Column {
            Text("Active Hours")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                var showStartTimePicker by remember { mutableStateOf(false) }
                var showEndTimePicker by remember { mutableStateOf(false) }

                Column(modifier = Modifier.weight(1f)) {
                    Text("Start Time")
                    OutlinedButton(
                        onClick = { showStartTimePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(convertTo12Hour(settings.startTime))
                    }

                    if (showStartTimePicker) {
                        TimePickerDialog(
                            onDismissRequest = { showStartTimePicker = false },
                            onTimeSelected = { hour ->
                                viewModel.updateSettings(settings.copy(startTime = hour))
                                showStartTimePicker = false
                            },
                            initialHour = settings.startTime
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text("End Time")
                    OutlinedButton(
                        onClick = { showEndTimePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(convertTo12Hour(settings.endTime))
                    }

                    if (showEndTimePicker) {
                        TimePickerDialog(
                            onDismissRequest = { showEndTimePicker = false },
                            onTimeSelected = { hour ->
                                viewModel.updateSettings(settings.copy(endTime = hour))
                                showEndTimePicker = false
                            },
                            initialHour = settings.endTime
                        )
                    }
                }
            }
        }

        // Show in Apps
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Show in Other Apps")
            Switch(
                checked = settings.showInApps,
                onCheckedChange = { showInApps ->
                    viewModel.updateSettings(settings.copy(showInApps = showInApps))
                }
            )
        }

        // Duration
        Column {
            Text("Popup Duration (seconds)")
            Slider(
                value = settings.durationSeconds.toFloat(),
                onValueChange = { value ->
                    viewModel.updateSettings(settings.copy(durationSeconds = value.toInt()))
                },
                valueRange = 5f..30f,
                steps = 25
            )
            Text("${settings.durationSeconds} seconds")
        }

        // Switch to Dialogs After Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Switch to Dialogs After Cards")
            Switch(
                checked = settings.switchToDialogsAfterCards,
                onCheckedChange = { switchToDialogs ->
                    viewModel.updateSettings(settings.copy(switchToDialogsAfterCards = switchToDialogs))
                }
            )
        }

        // Start Dialog (only visible when switch to dialogs is enabled)
        if (settings.switchToDialogsAfterCards) {
            Column {
                Text("Start Dialog")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberPicker(
                        value = settings.startDialog,
                        onValueChange = { value ->
                            viewModel.updateSettings(settings.copy(startDialog = value))
                        },
                        range = 0..(totalDialogs.coerceAtLeast(1) - 1),
                        displayText = { (it + 1).toString() }
                    )
                    Text("Starting from dialog ${settings.startDialog + 1}")
                }
            }
        }

        // Restart Service Button
        if (settings.isEnabled) {
            Button(
                onClick = {
                    val intent = Intent(context, PopupNotificationService::class.java)
                    intent.action = "START_POPUP_SERVICE"
                    context.startService(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restart Popup Service")
            }
        }

        // Add some bottom padding for better scrolling experience
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    onTimeSelected: (Int) -> Unit,
    initialHour: Int
) {
    var selectedHour by remember { mutableStateOf(initialHour) }
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select Time") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NumberPicker(
                    value = selectedHour,
                    onValueChange = { selectedHour = it },
                    range = 0..23,
                    displayText = { hour -> convertTo12Hour(hour) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onTimeSelected(selectedHour) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun NumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    displayText: (Int) -> String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = {
                val newValue = if (value >= range.last) range.first else value + 1
                onValueChange(newValue)
            }
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Increase"
            )
        }

        Text(
            text = displayText(value),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        IconButton(
            onClick = {
                val newValue = if (value <= range.first) range.last else value - 1
                onValueChange(newValue)
            }
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Decrease"
            )
        }
    }
}

private fun convertTo12Hour(hour24: Int): String {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, hour24)
    calendar.set(Calendar.MINUTE, 0)
    
    val format = SimpleDateFormat("h:mm a", Locale.getDefault())
    return format.format(calendar.time)
}
