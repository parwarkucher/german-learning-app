package com.parwar.german_learning.data.models

enum class TestMode {
    SEQUENTIAL,  // Standard sequential card testing
    RANDOM,      // Random card selection
    WRITING,     // User completes missing sections in cards
    READING,     // User reads the card and marks if they read it correctly
    LISTENING,   // User types what they hear from the card
    DIALOG_QUESTION, // User writes questions for dialog pairs
    DIALOG_ANSWER   // User writes answers for dialog pairs
}
