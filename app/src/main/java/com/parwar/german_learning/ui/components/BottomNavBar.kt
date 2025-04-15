package com.parwar.german_learning.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.parwar.german_learning.R
import com.parwar.german_learning.ui.navigation.Screen

@Composable
fun BottomNavBar(
    navController: NavController,
    currentRoute: String,
    onRouteSelected: (String) -> Unit
) {
    NavigationBar {
        val items = listOf(
            Triple(Screen.Cards.route, "Cards", R.drawable.ic_cards),
            Triple(Screen.Dialog.route, "Dialog", R.drawable.ic_cards),
            Triple(Screen.Practice.route, "Practice", R.drawable.ic_practice),
            Triple(Screen.Chat.route, "Chat", R.drawable.ic_chat),
            Triple(Screen.Progress.route, "Progres", R.drawable.ic_progress),
            Triple(Screen.Gym.route, "Gym", R.drawable.ic_gym),
            Triple(Screen.Popup.route, "Popup", R.drawable.ic_popup),
            Triple(Screen.Sync.route, "Sync", R.drawable.ic_sync)
        )

        items.forEach { (route, title, icon) ->
            NavigationBarItem(
                icon = { Icon(painter = painterResource(id = icon), contentDescription = title) },
                label = { Text(title, style = MaterialTheme.typography.labelSmall) },
                selected = currentRoute == route,
                onClick = {
                    if (currentRoute != route) {
                        onRouteSelected(route)
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    }
}
