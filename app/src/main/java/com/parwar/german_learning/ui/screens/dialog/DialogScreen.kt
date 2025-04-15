package com.parwar.german_learning.ui.screens.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.parwar.german_learning.data.models.Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogScreen(
    navController: NavController,
    viewModel: DialogViewModel = hiltViewModel()
) {
    var showDialog by remember { mutableStateOf(false) }
    var selectedDialog by remember { mutableStateOf<Dialog?>(null) }
    val dialogs by viewModel.dialogs.collectAsState()
    val mediaManager = viewModel.mediaManager
    val translationManager = viewModel.translationManager
    var expandedDialogId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                selectedDialog = null
                showDialog = true 
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Dialog")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Dialogs",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(dialogs) { index, dialog ->
                    val isExpanded = expandedDialogId == dialog.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                expandedDialogId = if (isExpanded) null else dialog.id
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Row {
                                    // Edit button
                                    IconButton(
                                        onClick = {
                                            selectedDialog = dialog
                                            showDialog = true
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    // Delete button
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteDialog(dialog)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // German text is always visible
                            Text(
                                text = dialog.germanText,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // English translation and other details are only visible when expanded
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = dialog.englishText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (dialog.participants.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Participants: ${dialog.participants.joinToString(", ")}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Difficulty: ${dialog.difficulty}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (dialog.category.isNotEmpty()) {
                                            Text(
                                                text = "Category: ${dialog.category}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showDialog) {
            DialogInputForm(
                initialDialog = selectedDialog,
                onDismiss = { showDialog = false },
                onSave = { dialog ->
                    if (selectedDialog == null) {
                        viewModel.addDialog(dialog)
                    } else {
                        viewModel.updateDialog(dialog)
                    }
                    showDialog = false
                },
                mediaManager = mediaManager,
                translationManager = translationManager
            )
        }
    }
}
