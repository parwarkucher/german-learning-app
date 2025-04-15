package com.parwar.german_learning

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.parwar.german_learning.data.AppDatabase
import com.parwar.german_learning.ui.components.BottomNavBar
import com.parwar.german_learning.ui.navigation.AppNavigation
import com.parwar.german_learning.ui.theme.GermanLearningTheme
import com.parwar.german_learning.utils.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var preferencesManager: PreferencesManager

    private var hasStoragePermissions = false
    private var uiInitialized = false
    private var database: AppDatabase? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            hasStoragePermissions = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requestManageStoragePermission()
            } else {
                initializeStorageAndApp()
            }
        } else {
            showPermissionRationale()
        }
    }

    private val manageStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                hasStoragePermissions = true
                initializeStorageAndApp()
            } else {
                showPermissionRationale()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                hasStoragePermissions = true
                initializeStorageAndApp()
            } else {
                requestManageStoragePermission()
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )

            val allGranted = permissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }

            if (allGranted) {
                hasStoragePermissions = true
                initializeStorageAndApp()
            } else {
                requestPermissionLauncher.launch(permissions)
            }
        }
    }

    private fun requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    addCategory("android.intent.category.DEFAULT")
                    data = Uri.parse("package:$packageName")
                }
                manageStoragePermissionLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStoragePermissionLauncher.launch(intent)
            }
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Storage Permission Required")
            .setMessage("This app needs storage access to save your learning progress and flashcards. Without this permission, some features may not work properly.")
            .setPositiveButton("Grant Permission") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    requestManageStoragePermission()
                } else {
                    requestPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                }
            }
            .setNegativeButton("Exit App") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun initializeStorageAndApp() {
        if (!uiInitialized) {
            try {
                preferencesManager.initializeExternalStorage()
                // Initialize database with external storage if permissions are granted
                database = AppDatabase.getDatabase(
                    applicationContext,
                    useExternal = hasStoragePermissions
                )
                showAppUI()
                uiInitialized = true
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to initialize storage: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun showAppUI() {
        setContent {
            GermanLearningTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    var currentRoute by remember { mutableStateOf("cards") }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            BottomNavBar(
                                navController = navController,
                                currentRoute = currentRoute,
                                onRouteSelected = { route ->
                                    currentRoute = route
                                }
                            )
                        }
                    ) { paddingValues ->
                        AppNavigation(
                            navController = navController,
                            modifier = Modifier.padding(paddingValues),
                            onRouteChanged = { route ->
                                currentRoute = route
                            }
                        )
                    }
                }
            }
        }
    }
}
