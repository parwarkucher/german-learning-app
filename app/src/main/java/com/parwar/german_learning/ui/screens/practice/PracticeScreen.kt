package com.parwar.german_learning.ui.screens.practice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.parwar.german_learning.data.models.DialogPair
import com.parwar.german_learning.data.models.FlashCard
import com.parwar.german_learning.data.models.TestMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel = hiltViewModel()
) {
    val currentCard by viewModel.currentCard.collectAsState()
    val showAnswer by viewModel.showAnswer.collectAsState()
    val quizStats by viewModel.quizStats.collectAsState()
    val testMode by viewModel.testMode.collectAsState()
    val userInput by viewModel.userInput.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val feedbackMessage by viewModel.feedbackMessage.collectAsState()
    var isTestActive by remember { mutableStateOf(false) }
    var showStartDialog by remember { mutableStateOf(false) }
    var selectedTestMode by remember { mutableStateOf<TestMode?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Stats Display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Score: ${quizStats.correctAnswers}/${quizStats.totalQuestions}",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Accuracy: ${quizStats.accuracyPercentage}%",
                    style = MaterialTheme.typography.bodyLarge
                )
                feedbackMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (message.startsWith("Correct")) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (!isTestActive) {
            // Writing Practice Box
            PracticeBox(
                title = "Writing Practice",
                description = "Test your German writing skills",
                icon = Icons.Default.Edit,
                onStartTest = {
                    selectedTestMode = TestMode.WRITING
                    showStartDialog = true
                }
            )

            // Reading Practice Box
            PracticeBox(
                title = "Reading Practice",
                description = "Practice reading German texts",
                icon = Icons.Default.MenuBook,
                onStartTest = {
                    selectedTestMode = TestMode.READING
                    showStartDialog = true
                }
            )

            // Listening Practice Box
            PracticeBox(
                title = "Listening Practice",
                description = "Test your German listening comprehension",
                icon = Icons.Default.Hearing,
                onStartTest = {
                    selectedTestMode = TestMode.LISTENING
                    showStartDialog = true
                }
            )

            // Dialog Practice Box
            PracticeBox(
                title = "Dialog Practice",
                description = "Practice with dialog pairs - questions and answers",
                icon = Icons.Default.Chat,
                onStartTest = {
                    selectedTestMode = TestMode.DIALOG_QUESTION
                    showStartDialog = true
                }
            )
        } else {
            // Active Test Content
            currentCard?.let { card ->
                when (testMode) {
                    TestMode.WRITING -> {
                        if (card is FlashCard) {
                            WritingTest(
                                card = card,
                                userInput = userInput,
                                onUserInput = viewModel::updateUserInput,
                                onSubmit = viewModel::checkAnswer
                            )
                        }
                    }
                    TestMode.READING -> {
                        if (card is FlashCard) {
                            ReadingTest(
                                card = card,
                                onCorrect = { viewModel.submitAnswer(true) },
                                onIncorrect = { viewModel.submitAnswer(false) },
                                isListening = isListening,
                                onStartListening = viewModel::startListening
                            )
                        }
                    }
                    TestMode.LISTENING -> {
                        if (card is FlashCard) {
                            ListeningTest(
                                userInput = userInput,
                                isListening = isListening,
                                onUserInput = viewModel::updateUserInput,
                                onStartListening = viewModel::startListening,
                                onSubmit = viewModel::checkAnswer
                            )
                        }
                    }
                    TestMode.DIALOG_QUESTION, TestMode.DIALOG_ANSWER -> {
                        if (card is DialogPair) {
                            DialogPracticeTest(
                                dialogPair = card,
                                userInput = userInput,
                                testMode = testMode,
                                onUserInput = viewModel::updateUserInput,
                                onSubmit = viewModel::checkAnswer
                            )
                        }
                    }
                    else -> {
                        if (card is FlashCard) {
                            FlashCardContent(
                                card = card,
                                showAnswer = showAnswer,
                                onShowAnswer = viewModel::showAnswer,
                                onAnswered = viewModel::submitAnswer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Finish Test Button
            Button(
                onClick = {
                    viewModel.finishSession()
                    isTestActive = false
                },
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Text("Finish Test")
            }
        }
    }

    if (showStartDialog) {
        when (selectedTestMode) {
            TestMode.DIALOG_QUESTION, TestMode.DIALOG_ANSWER -> {
                DialogTestConfigDialog(
                    onDismiss = { showStartDialog = false },
                    onStart = { startCard, isRandom, isQuestion ->
                        viewModel.setTestMode(if (isQuestion) TestMode.DIALOG_QUESTION else TestMode.DIALOG_ANSWER)
                        viewModel.setRandomMode(isRandom)
                        viewModel.setStartingCardNumber(startCard)
                        viewModel.startNewSession()
                        isTestActive = true
                        showStartDialog = false
                    }
                )
            }
            else -> {
                // Original test config dialog for writing, reading, and listening practice
                TestConfigDialog(
                    onDismiss = { showStartDialog = false },
                    onStart = { startCard, isRandom ->
                        viewModel.setTestMode(selectedTestMode ?: TestMode.WRITING)
                        viewModel.setRandomMode(isRandom)
                        viewModel.setStartingCardNumber(startCard)
                        viewModel.startNewSession()
                        isTestActive = true
                        showStartDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun PracticeBox(
    title: String,
    description: String,
    icon: ImageVector,
    onStartTest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        onClick = onStartTest
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .padding(end = 16.dp)
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onStartTest,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Start")
            }
        }
    }
}

@Composable
fun TestConfigDialog(
    onDismiss: () -> Unit,
    onStart: (startCard: Int, isRandom: Boolean) -> Unit
) {
    var startFromCardText by remember { mutableStateOf("1") }
    var isRandomOrder by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test Settings") },
        text = {
            Column {
                // Start from card number
                OutlinedTextField(
                    value = startFromCardText,
                    onValueChange = { startFromCardText = it },
                    label = { Text("Start from card number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // Random order checkbox
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isRandomOrder,
                        onCheckedChange = { isRandomOrder = it }
                    )
                    Text("Random order")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val startCard = startFromCardText.toIntOrNull() ?: 1
                    onStart(startCard, isRandomOrder)
                }
            ) {
                Text("Start Test")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DialogTestConfigDialog(
    onDismiss: () -> Unit,
    onStart: (startCard: Int, isRandom: Boolean, isQuestion: Boolean) -> Unit
) {
    var startCardNumber by remember { mutableStateOf("1") }
    var isRandomOrder by remember { mutableStateOf(false) }
    var isQuestionMode by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test Settings") },
        text = {
            Column {
                OutlinedTextField(
                    value = startCardNumber,
                    onValueChange = { startCardNumber = it },
                    label = { Text("Start from card number") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isRandomOrder,
                        onCheckedChange = { isRandomOrder = it }
                    )
                    Text("Random order")
                }

                Text(
                    text = "Practice mode:",
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isQuestionMode,
                        onClick = { isQuestionMode = true }
                    )
                    Text(
                        text = "Practice answering questions",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !isQuestionMode,
                        onClick = { isQuestionMode = false }
                    )
                    Text(
                        text = "Practice asking questions",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val startCard = startCardNumber.toIntOrNull() ?: 1
                    onStart(startCard, isRandomOrder, isQuestionMode)
                }
            ) {
                Text("Start Test")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun WritingTest(
    card: FlashCard,
    userInput: String,
    onUserInput: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Complete the phrase:",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = card.englishText,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = userInput,
                onValueChange = onUserInput,
                label = { Text("Your answer") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onSubmit,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Check Answer")
            }
        }
    }
}

@Composable
fun ReadingTest(
    card: FlashCard,
    onCorrect: () -> Unit,
    onIncorrect: () -> Unit,
    isListening: Boolean,
    onStartListening: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Read the following phrase:",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = card.germanText,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            
            IconButton(
                onClick = onStartListening,
                modifier = Modifier
                    .size(48.dp)
                    .padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = if (isListening) "Stop" else "Listen to correct pronunciation",
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "Tap to hear correct pronunciation",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onIncorrect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Read Incorrectly")
                }
                Button(
                    onClick = onCorrect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Read Correctly")
                }
            }
        }
    }
}

@Composable
fun ListeningTest(
    userInput: String,
    isListening: Boolean,
    onUserInput: (String) -> Unit,
    onStartListening: () -> Unit,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Listen and type what you hear:",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            IconButton(
                onClick = onStartListening,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = if (isListening) "Stop" else "Listen",
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = if (isListening) "Tap to listen again" else "Tap to listen",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = userInput,
                onValueChange = onUserInput,
                label = { Text("Type what you heard") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onSubmit() }
                )
            )
            Button(
                onClick = onSubmit,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Check Answer")
            }
        }
    }
}

@Composable
fun DialogPracticeTest(
    dialogPair: DialogPair,
    userInput: String,
    testMode: TestMode,
    onUserInput: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Display the prompt based on test mode
        Text(
            text = if (testMode == TestMode.DIALOG_QUESTION) {
                "Write the question for this answer:"
            } else {
                "Write the answer for this question:"
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Display the known part (answer or question)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (testMode == TestMode.DIALOG_QUESTION) {
                        dialogPair.germanAnswer
                    } else {
                        dialogPair.germanQuestion
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = if (testMode == TestMode.DIALOG_QUESTION) {
                        dialogPair.englishAnswer
                    } else {
                        dialogPair.englishQuestion
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Input field for user's answer
        OutlinedTextField(
            value = userInput,
            onValueChange = onUserInput,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            label = { Text("Your ${if (testMode == TestMode.DIALOG_QUESTION) "Question" else "Answer"}") },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSubmit() }
            )
        )

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Submit")
        }
    }
}

@Composable
fun FlashCardContent(
    card: FlashCard,
    showAnswer: Boolean,
    onShowAnswer: () -> Unit,
    onAnswered: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = card.germanText,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            if (!showAnswer) {
                Button(
                    onClick = onShowAnswer,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Show Answer")
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = card.englishText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { onAnswered(false) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Incorrect")
                    }
                    Button(
                        onClick = { onAnswered(true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Correct")
                    }
                }
            }
        }
    }
}
