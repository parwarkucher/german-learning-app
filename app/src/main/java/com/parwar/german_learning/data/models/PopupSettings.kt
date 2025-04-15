package com.parwar.german_learning.data.models

data class PopupSettings(
    val isEnabled: Boolean = false,
    val frequency: Int = 60, // minutes between popups
    val startTime: Int = 9, // 24-hour format
    val endTime: Int = 0, // 24-hour format (0 = 12 AM)
    val showInApps: Boolean = true,
    val durationSeconds: Int = 10,
    val useRandomCards: Boolean = true, // true for random, false for sequential from startCard
    val startCard: Int = 0, // index to start from when not random
    val switchToDialogsAfterCards: Boolean = false, // new setting for switching to dialogs
    val startDialog: Int = 0 // index of dialog to start from when switching to dialogs
)
