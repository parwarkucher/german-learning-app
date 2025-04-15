package com.parwar.german_learning.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.parwar.german_learning.ui.screens.cards.CardsScreen
import com.parwar.german_learning.ui.screens.practice.PracticeScreen
import com.parwar.german_learning.ui.screens.chat.ChatScreen
import com.parwar.german_learning.ui.screens.progress.ProgressScreen
import com.parwar.german_learning.ui.screens.gym.GymScreen
import com.parwar.german_learning.ui.screens.popup.PopupScreen
import com.parwar.german_learning.ui.screens.dialog.DialogScreen
import com.parwar.german_learning.ui.screens.sync.SyncScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onRouteChanged: (String) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Cards.route,
        modifier = modifier
    ) {
        composable(Screen.Cards.route) {
            onRouteChanged(Screen.Cards.route)
            CardsScreen(navController)
        }
        composable(Screen.Dialog.route) {
            onRouteChanged(Screen.Dialog.route)
            DialogScreen(navController)
        }
        composable(Screen.Practice.route) {
            onRouteChanged(Screen.Practice.route)
            PracticeScreen()
        }
        composable(Screen.Chat.route) {
            onRouteChanged(Screen.Chat.route)
            ChatScreen(navController)
        }
        composable(Screen.Progress.route) {
            onRouteChanged(Screen.Progress.route)
            ProgressScreen()
        }
        composable(Screen.Gym.route) {
            onRouteChanged(Screen.Gym.route)
            GymScreen(navController)
        }
        composable(Screen.Popup.route) {
            onRouteChanged(Screen.Popup.route)
            PopupScreen()
        }
        composable(Screen.Sync.route) {
            onRouteChanged(Screen.Sync.route)
            SyncScreen()
        }
    }
}

sealed class Screen(val route: String) {
    object Cards : Screen("cards")
    object Dialog : Screen("dialog")
    object Practice : Screen("practice")
    object Chat : Screen("chat")
    object Progress : Screen("progress")
    object Gym : Screen("gym")
    object Popup : Screen("popup")
    object Sync : Screen("sync")
}
