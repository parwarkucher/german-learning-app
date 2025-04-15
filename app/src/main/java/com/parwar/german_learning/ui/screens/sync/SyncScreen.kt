package com.parwar.german_learning.ui.screens.sync

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.parwar.german_learning.services.GoogleDriveService

@Composable
fun SyncScreen(
    viewModel: SyncViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val syncMessages = viewModel.syncMessages.collectAsState()
    val isSignedIn = viewModel.isSignedIn.collectAsState()
    
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                Log.d("SyncScreen", "Sign in successful: ${account.email}")
                viewModel.onSignInSuccess()
                Toast.makeText(context, "Signed in as ${account.email}", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("SyncScreen", "Sign in failed: account is null")
                viewModel.onSignInError("Sign in failed: account is null")
                Toast.makeText(context, "Sign in failed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Log.e("SyncScreen", "Sign in error: ${e.message}", e)
            val message = when (e.statusCode) {
                7 -> "Network error - please check your connection"
                10 -> "Developer error - please check configuration"
                12501 -> "Sign in cancelled"
                12500 -> "Sign in currently in progress"
                else -> "Sign in failed: ${e.statusCode}"
            }
            viewModel.onSignInError(message)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("SyncScreen", "Unexpected error during sign in", e)
            viewModel.onSignInError("Unexpected error: ${e.message}")
            Toast.makeText(context, "Unexpected error during sign in", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isSignedIn.value) {
            Button(
                onClick = { viewModel.signOut() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("SIGN OUT")
            }
            
            Button(
                onClick = { viewModel.startSync() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SYNC WITH GOOGLE DRIVE")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Auto-sync when app opens/closes")
                Switch(
                    checked = viewModel.autoSync.collectAsState().value,
                    onCheckedChange = { viewModel.setAutoSync(it) }
                )
            }

            Text(
                text = "Last synced: ${viewModel.lastSyncTime.collectAsState().value}",
                style = MaterialTheme.typography.bodyMedium
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(syncMessages.value) { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Button(
                onClick = {
                    signInLauncher.launch(viewModel.getSignInIntent())
                }
            ) {
                Text("SIGN IN WITH GOOGLE")
            }
        }
    }
}
