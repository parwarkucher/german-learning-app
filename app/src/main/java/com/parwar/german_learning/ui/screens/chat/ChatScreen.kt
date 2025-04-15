package com.parwar.german_learning.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.parwar.german_learning.data.models.ChatMessage
import com.parwar.german_learning.data.models.ChatMode
import com.parwar.german_learning.network.ModelInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalConfiguration
import android.content.Context
import android.content.Intent
import java.util.*
import android.content.ComponentName

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val chatState by viewModel.chatState.collectAsState()
    val autoReadEnabled by viewModel.autoReadEnabled.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val savedChats by viewModel.savedChats.collectAsState()
    val showSaveChatDialog by viewModel.showSaveChatDialog.collectAsState()
    var messageInput by remember { mutableStateOf("") }
    var showModeSelection by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    var showModelDropdown by remember { mutableStateOf(false) }
    var showModelInfo by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(chatState.config.selectedModel) }
    var showSettings by remember { mutableStateOf(false) }
    var showCardRangeDialog by remember { mutableStateOf(false) }
    var showSavedChatsDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    val germanVoices by viewModel.availableGermanVoices.collectAsState()
    val selectedVoiceIndex by viewModel.selectedVoiceIndex.collectAsState()
    val currentPitch by viewModel.currentPitch.collectAsState()
    val context = LocalView.current.context

    // Speed Control Dialog
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Speech Rate") },
            text = {
                Column {
                    Text("Current speed: ${speechRate}x")
                    Slider(
                        value = speechRate,
                        onValueChange = { viewModel.setSpeechRate(it) },
                        valueRange = 0.5f..2.0f,
                        steps = 5
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Voice Selection Dialog
    if (showVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showVoiceDialog = false },
            title = { Text("Voice Settings") },
            text = {
                Column {
                    Text("Found ${germanVoices.size} available voices", style = MaterialTheme.typography.bodySmall)
                    
                    // Voice selection
                    if (germanVoices.isEmpty()) {
                        // No voices found
                        Text(
                            text = "No voices found. Please download them using the instructions below.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                        
                        // Instructions to fix
                        Text(
                            text = "On Samsung devices:",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        
                        Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp)) {
                            Text(
                                text = "1. Go to Settings > General management > Language > Text-to-speech",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "2. Select Samsung TTS from the list",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "3. Tap on Settings icon (⚙️) next to Samsung TTS",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "4. Select \"Install language data\" or \"Download\"",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "5. Download \"German\" language pack",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "For higher quality voices:",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        
                        Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp)) {
                            Text(
                                text = "1. In Samsung TTS settings, tap \"Voice settings\"",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "2. Select \"German\" language",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "3. Choose \"Standard\" or \"High quality\" voice option",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = {
                                    // Open Samsung TTS settings directly if possible
                                    val intent = Intent().apply {
                                        component = ComponentName(
                                            "com.samsung.android.honeyboard",
                                            "com.samsung.android.honeyboard.service.setting.SamsungTTSSettings"
                                        )
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            // Try the general TTS settings
                                            val ttsIntent = Intent().apply {
                                                action = "com.android.settings.TTS_SETTINGS"
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(ttsIntent)
                                        } catch (e: Exception) {
                                            // If all else fails, open main settings
                                            val mainIntent = Intent(android.provider.Settings.ACTION_SETTINGS)
                                            mainIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            context.startActivity(mainIntent)
                                        }
                                    }
                                }
                            ) {
                                Text("Open Samsung TTS")
                            }
                            
                            Button(
                                onClick = {
                                    // Refresh voice list after downloading
                                    viewModel.reinitializeTextToSpeech()
                                    // Small delay for UI update
                                    android.os.Handler().postDelayed({
                                        // This will create a small UI "blink" to show action was taken
                                        showVoiceDialog = false
                                        android.os.Handler().postDelayed({
                                            showVoiceDialog = true
                                        }, 100)
                                    }, 300)
                                }
                            ) {
                                Text("Refresh Voices")
                            }
                        }
                    } else {
                        // Voice list
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            items(germanVoices.size) { index ->
                                val voice = germanVoices[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setGermanVoice(index) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = index == selectedVoiceIndex,
                                        onClick = { viewModel.setGermanVoice(index) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = voice.name
                                                .substringAfterLast('/')
                                                .replace("-", " ")
                                                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = voice.locale.displayName,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    
                                    // Play test button
                                    IconButton(
                                        onClick = {
                                            viewModel.setGermanVoice(index)
                                            viewModel.speakGerman("Hallo, das ist ein Test.", true)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Test Voice",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Button(
                                onClick = {
                                    // Open dialog with download instructions
                                    // This will show even when voices are found, to help get more/better voices
                                    val intent = Intent().apply {
                                        component = ComponentName(
                                            "com.samsung.android.honeyboard",
                                            "com.samsung.android.honeyboard.service.setting.SamsungTTSSettings"
                                        )
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val ttsIntent = Intent().apply {
                                                action = "com.android.settings.TTS_SETTINGS"
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(ttsIntent)
                                        } catch (e: Exception) {
                                            val mainIntent = Intent(android.provider.Settings.ACTION_SETTINGS)
                                            mainIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            context.startActivity(mainIntent)
                                        }
                                    }
                                }
                            ) {
                                Text("Download More Voices")
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Pitch control
                    Text("Voice Pitch", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Current pitch: ${String.format("%.1f", currentPitch)}")
                        
                        // Test button for current pitch - fixed to apply pitch before testing
                        IconButton(
                            onClick = {
                                // Apply the pitch first, then speak
                                viewModel.setPitch(currentPitch)
                                // Small delay to ensure pitch is set before speaking
                                android.os.Handler().postDelayed({
                                    viewModel.speakGerman("Hallo, das ist ein Test.", true)
                                }, 100)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "Test Pitch",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    Slider(
                        value = currentPitch,
                        onValueChange = { 
                            viewModel.setPitch(it) 
                        },
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showVoiceDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Bar with Settings
        TopAppBar(
            title = {
                val tokenInfo by viewModel.tokenInfo.collectAsState()
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$${String.format("%.4f", tokenInfo.first)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$${String.format("%.4f", tokenInfo.second)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            actions = {
                // Auto-read toggle
                IconButton(
                    onClick = { viewModel.toggleAutoRead() }
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Toggle Auto-read",
                        tint = if (autoReadEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                // Speed control
                if (autoReadEnabled) {
                    IconButton(onClick = { showSpeedDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Speech Rate",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                // Voice selection
                IconButton(onClick = { showVoiceDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Voice Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                // Save Chat Button
                IconButton(onClick = { viewModel.showSaveChatDialog() }) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save Chat",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                // Load Chat Button
                IconButton(onClick = { showSavedChatsDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Load Saved Chat",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                // Card Range Button
                IconButton(onClick = { showCardRangeDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Set Card Range"
                    )
                }
                // Settings Button
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
            }
        )

        // Save Chat Dialog
        if (showSaveChatDialog) {
            var chatName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { viewModel.hideSaveChatDialog() },
                title = { Text("Save Chat") },
                text = {
                    OutlinedTextField(
                        value = chatName,
                        onValueChange = { chatName = it },
                        label = { Text("Chat Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { 
                            if (chatName.isNotBlank()) {
                                viewModel.saveCurrentChat(chatName)
                            }
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideSaveChatDialog() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Load Saved Chats Dialog
        if (showSavedChatsDialog) {
            AlertDialog(
                onDismissRequest = { showSavedChatsDialog = false },
                title = { Text("Saved Chats") },
                text = {
                    LazyColumn {
                        items(savedChats) { chat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        viewModel.loadSavedChat(chat.id)
                                        showSavedChatsDialog = false
                                    }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = chat.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Messages: ${chat.messages.size}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deleteSavedChat(chat) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Chat",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            if (savedChats.indexOf(chat) < savedChats.size - 1) {
                                Divider()
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSavedChatsDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Card Range Dialog
        if (showCardRangeDialog) {
            var startIndex by remember { mutableStateOf(chatState.cardRange.startIndex.toString()) }
            var endIndex by remember { mutableStateOf(
                if (chatState.cardRange.endIndex == Int.MAX_VALUE) "" 
                else chatState.cardRange.endIndex.toString()
            ) }
            var enabled by remember { mutableStateOf(chatState.cardRange.enabled) }

            AlertDialog(
                onDismissRequest = { showCardRangeDialog = false },
                title = { Text("Set Card Range") },
                text = {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = enabled,
                                onCheckedChange = { enabled = it }
                            )
                            Text("Enable card range")
                        }
                        if (enabled) {
                            OutlinedTextField(
                                value = startIndex,
                                onValueChange = { startIndex = it },
                                label = { Text("Start Index (0 = first)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = endIndex,
                                onValueChange = { endIndex = it },
                                label = { Text("End Index (empty = last)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val start = startIndex.toIntOrNull() ?: 0
                        val end = endIndex.toIntOrNull() ?: Int.MAX_VALUE
                        viewModel.setCardRange(start, end, enabled)
                        showCardRangeDialog = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCardRangeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showModeSelection) {
            // API Configuration Section
            OutlinedTextField(
                value = chatState.config.apiKey,
                onValueChange = { viewModel.updateConfig(apiKey = it) },
                label = { Text("OpenRouter API Key") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Model Selection Dropdown
            ExposedDropdownMenuBox(
                expanded = showModelDropdown,
                onExpandedChange = { showModelDropdown = it }
            ) {
                OutlinedTextField(
                    value = selectedModel.name,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("AI Model") },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { showModelInfo = true }) {
                                Icon(Icons.Default.Info, "Model Info")
                            }
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showModelDropdown)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = showModelDropdown,
                    onDismissRequest = { showModelDropdown = false }
                ) {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text(model.name)
                                    Text(
                                        text = "Context: ${model.contextSize} tokens",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            onClick = { 
                                viewModel.updateConfig(model = model)
                                selectedModel = model
                                showModelDropdown = false
                            }
                        )
                    }
                }
            }

            if (showModelInfo) {
                AlertDialog(
                    onDismissRequest = { showModelInfo = false },
                    title = { Text(selectedModel.name) },
                    text = {
                        Column {
                            Text("Model ID: ${selectedModel.id}")
                            Text("Context Size: ${selectedModel.contextSize} tokens")
                            Text("Pricing:")
                            if (selectedModel.pricing.inputPrice == 0.0 && 
                                selectedModel.pricing.outputPrice == 0.0) {
                                Text("Free")
                            } else {
                                Text("Input: $${selectedModel.pricing.inputPrice} per million tokens")
                                Text("Output: $${selectedModel.pricing.outputPrice} per million tokens")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showModelInfo = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chat Mode Selection
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            viewModel.setChatMode(ChatMode.CARDS_BASED)
                            showModeSelection = false
                        },
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Text("Chat Based on Cards")
                    }

                    Button(
                        onClick = {
                            viewModel.setChatMode(ChatMode.DIALOG_BASED)
                            showModeSelection = false
                        },
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    ) {
                        Text("Chat Based on Dialogs")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        viewModel.setChatMode(ChatMode.CARDS_AND_DIALOG_BASED)
                        showModeSelection = false
                    },
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Text("Chat Based on Cards & Dialogs")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        viewModel.setChatMode(ChatMode.STORY_CHAT)
                        showModeSelection = false
                    },
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Text("Story Chat")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        viewModel.setChatMode(ChatMode.GENERAL)
                        showModeSelection = false
                    },
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Text("General Chat")
                }
            }
        } else {
            // Chat Interface
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (chatState.mode) {
                            ChatMode.CARDS_BASED -> "Cards-Based Chat"
                            ChatMode.DIALOG_BASED -> "Dialog-Based Chat"
                            ChatMode.CARDS_AND_DIALOG_BASED -> "Cards & Dialog-Based Chat"
                            ChatMode.STORY_CHAT -> "Story Chat"
                            else -> "General Chat"
                        },
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Using ${chatState.config.selectedModel.name}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                TextButton(
                    onClick = { showModeSelection = true }
                ) {
                    Text("Change Mode")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Messages List
            val configuration = LocalConfiguration.current
            val screenWidth = configuration.screenWidthDp.dp
            val isLargeTablet = screenWidth > 1000.dp
            
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Add horizontal padding for large tablets
                    .padding(horizontal = if (isLargeTablet) 16.dp else 0.dp),
                state = listState,
                reverseLayout = false
            ) {
                items(chatState.messages) { message ->
                    ChatMessageItem(message, viewModel)
                }
            }

            // Error Message
            chatState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Loading Indicator
            if (chatState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }

            // Message Input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type your message...") }
                )

                IconButton(
                    onClick = {
                        viewModel.sendMessage(messageInput)
                        messageInput = ""
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val backgroundColor = if (message.isUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.secondaryContainer

    var showContextMenu by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf("") }
    var contextMenuPosition by remember { mutableStateOf(Offset.Zero) }
    val view = LocalView.current
    
    // Get screen width to make chat width responsive
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    
    // Calculate max width based on screen size
    // For tablets (like Tab S9 Ultra), use a larger percentage of screen width
    val isTablet = screenWidth > 600.dp
    // Check for very large tablets like Tab S9 Ultra (which has a very wide screen)
    val isLargeTablet = screenWidth > 1000.dp
    
    val maxWidth = when {
        isLargeTablet -> {
            // Use 90% of screen width for very large tablets like Tab S9 Ultra
            (screenWidth * 0.9f)
        }
        isTablet -> {
            // Use 80% of screen width for regular tablets
            (screenWidth * 0.8f)
        }
        else -> {
            // Use fixed width for phones
            280.dp
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .padding(
                    // Add more padding for larger screens
                    horizontal = if (isLargeTablet) 16.dp else 12.dp,
                    vertical = if (isLargeTablet) 16.dp else 12.dp
                )
                .widthIn(max = maxWidth)
        ) {
            if (!message.isUser) {
                // Split the content into lines
                val lines = message.content.split("\n")
                Column {
                    lines.forEach { line ->
                        when {
                            // Story chat mode - German story section or paragraph marker
                            line.trim().startsWith("🇩🇪") || line.trim().startsWith("📝 Story") -> {
                                // Extract the entire German story for reading all at once
                                val messageLines = message.content.split("\n")
                                var completeGermanStory = ""
                                
                                // Collect all German paragraphs
                                for (storyLine in messageLines) {
                                    if (storyLine.trim().startsWith("🇩🇪") && 
                                        !storyLine.contains("Story") && 
                                        !storyLine.contains("-------------------------")) {
                                        // Add German paragraph text
                                        completeGermanStory += storyLine.substringAfter("🇩🇪").trim() + " "
                                    }
                                }
                                
                                // For story header (title or story marker)
                                if (line.trim().startsWith("📝 Story") || line.contains("Story")) {
                                    Row(
                                        modifier = Modifier
                                            .clickable { 
                                                // Read the entire German story
                                                viewModel.speakGerman(completeGermanStory.trim(), asStoryTeller = true)
                                            }
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = line,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                } else {
                                    // For individual German paragraph
                                    val germanText = line.substringAfter("🇩🇪").trim()
                                    Row(
                                        modifier = Modifier
                                            .clickable { 
                                                viewModel.speakGerman(germanText, asStoryTeller = true)
                                            }
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = line,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                            // Story content - paragraphs without markers
                            !line.trim().startsWith("🇩🇪") && 
                            !line.trim().startsWith("🇬🇧") && 
                            !line.trim().startsWith("📋") && 
                            !line.trim().startsWith("❓") && 
                            !line.contains("-------------------------") &&
                            message.content.contains("📝 Story") &&
                            line.trim().isNotEmpty() && 
                            !line.trim().startsWith("Format your story") -> {
                                Row(
                                    modifier = Modifier
                                        .clickable { viewModel.speakGerman(line.trim(), asStoryTeller = true) }
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = line,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                            // German lines start with "German:"
                            line.trim().startsWith("German:", ignoreCase = true) -> {
                                val germanText = line.substringAfter(":").trim()
                                Row(
                                    modifier = Modifier
                                        .combinedClickable(
                                            onClick = { viewModel.speakGerman(germanText) },
                                            onLongClick = {
                                                selectedText = germanText
                                                showContextMenu = true
                                            }
                                        )
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = line,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showContextMenu,
                                        onDismissRequest = { showContextMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Copy") },
                                            onClick = {
                                                showContextMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Add to Cards") },
                                            onClick = {
                                                // Find English translation in the message
                                                val messageLines = message.content.split("\n")
                                                var englishLine = ""
                                                
                                                for (i in messageLines.indices) {
                                                    if (messageLines[i].trim().startsWith("German:") && 
                                                        messageLines[i].substringAfter(":").trim().contains(selectedText)) {
                                                        if (i + 1 < messageLines.size && messageLines[i + 1].trim().startsWith("English:")) {
                                                            englishLine = messageLines[i + 1].substringAfter(":").trim()
                                                            break
                                                        }
                                                    }
                                                }

                                                if (selectedText.isNotBlank() && englishLine.isNotBlank()) {
                                                    viewModel.addToFlashcards(selectedText, englishLine)
                                                }
                                                showContextMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            // Other lines (English translations and notes)
                            else -> {
                                SelectionContainer {
                                    Text(
                                        text = line,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            } else {
                SelectionContainer {
                    Text(
                        text = message.content,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
