package com.parwar.german_learning.ui.screens.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.parwar.german_learning.data.models.StudySession
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()
    val stats by viewModel.stats.collectAsState()
    var selectedSession by remember { mutableStateOf<StudySession?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Overall Progress Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                Text(
                    text = "Overall Progress",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Overall Stats
                Text(
                    text = "Overall Stats",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("Total Cards Reviewed: ${stats.totalCardsReviewed}")
                Text("Total Correct Answers: ${stats.totalCorrectAnswers}")
                Text("Average Accuracy: ${String.format("%.1f", stats.averageAccuracy)}%")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Practice Type Stats
                Text(
                    text = "Practice Type Performance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Listening Stats
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Listening",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${String.format("%.1f", stats.listeningStats.accuracy)}%",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    
                    // Reading Stats
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Reading",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${String.format("%.1f", stats.readingStats.accuracy)}%",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    
                    // Writing Stats
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Writing",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${String.format("%.1f", stats.writingStats.accuracy)}%",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        // Session History
        Text(
            text = "Session History",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        LazyColumn {
            items(sessions) { session ->
                SessionCard(
                    session = session,
                    onDelete = { viewModel.deleteSession(session) },
                    onClick = { selectedSession = session }
                )
            }
        }
    }

    // Show session details dialog when a session is selected
    selectedSession?.let { session ->
        SessionDetailsDialog(
            session = session,
            onDismiss = { selectedSession = null }
        )
    }
}

@Composable
fun SessionCard(
    session: StudySession,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = dateFormat.format(Date(session.startTime)),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Test Type: ${session.testMode?.name ?: "Standard"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Correct: ${session.correctAnswers}/${session.cardsReviewed}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Accuracy: ${String.format("%.1f", session.accuracy)}%",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete session"
                )
            }
        }
    }
}

@Composable
fun WrongAnswerCard(wrongAnswer: String) {
    val lines = wrongAnswer.split("\n")
    val question = lines.find { it.startsWith("Question:") }?.substringAfter("Question: ") ?: ""
    val correct = lines.find { it.startsWith("Correct:") }?.substringAfter("Correct: ") ?: ""
    val userAnswer = lines.find { it.startsWith("Your answer:") }?.substringAfter("Your answer: ") ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Your Answer:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = userAnswer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Correct Answer:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = correct,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun SessionDetailsDialog(
    session: StudySession,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f),
        title = {
            Column {
                Text(
                    text = "Test Details",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Divider(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                ) {
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    
                    // Session Info Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Session Information",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Date: ${dateFormat.format(Date(session.startTime))}")
                            Text("Test Type: ${session.testMode?.name ?: "Standard"}")
                            Text("Cards Reviewed: ${session.cardsReviewed}")
                            Text("Correct Answers: ${session.correctAnswers}")
                            Text("Wrong Answers: ${session.wrongAnswers}")
                            Text("Accuracy: ${String.format("%.1f", session.accuracy)}%")
                            Text("Total Study Time: ${session.totalStudyTime / 1000} seconds")
                        }
                    }
                    
                    if (!session.wrongAnswerDetails.isNullOrEmpty()) {
                        Text(
                            text = "Wrong Answers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        session.wrongAnswerDetails.split("\n\n").forEach { wrongAnswer ->
                            if (wrongAnswer.isNotBlank()) {
                                WrongAnswerCard(wrongAnswer)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
