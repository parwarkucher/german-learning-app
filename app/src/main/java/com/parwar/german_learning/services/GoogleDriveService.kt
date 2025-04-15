package com.parwar.german_learning.services

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.client.util.DateTime
import com.parwar.german_learning.BuildConfig
import com.parwar.german_learning.data.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.FileOutputStream
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase
) {
    companion object {
        private const val TAG = "GoogleDriveService"
    }

    private val appDir by lazy { java.io.File(Environment.getExternalStorageDirectory(), "GermanLearning") }
    
    private val googleSignInClient: GoogleSignInClient by lazy {
        Log.d(TAG, "Initializing GoogleSignInClient")
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestId()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .requestIdToken(BuildConfig.GOOGLE_CLIENT_ID)
            .build()
        
        GoogleSignIn.getClient(context, gso).also {
            Log.d(TAG, "GoogleSignInClient initialized")
        }
    }

    fun getSignInClient(): GoogleSignInClient = googleSignInClient.also {
        Log.d(TAG, "Getting SignInClient")
    }

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent.also {
        Log.d(TAG, "Getting SignInIntent")
    }

    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        Log.d(TAG, "Checking sign-in status: ${account != null}, email: ${account?.email}")
        return account != null
    }

    suspend fun sync(): Flow<String> = flow {
        Log.d(TAG, "Starting sync process")
        emit("Starting sync process...")
        val account = GoogleSignIn.getLastSignedInAccount(context)
        
        if (account == null) {
            Log.w(TAG, "Not signed in during sync attempt")
            emit("Not signed in")
            return@flow
        }

        Log.d(TAG, "Syncing with account: ${account.email}")
        emit("Getting credentials for account: ${account.email}")
        
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        ).apply {
            selectedAccount = account.account
        }

        emit("Building Drive service...")
        val drive = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("German Learning")
            .build()

        emit("Looking for German Learning folder in Drive...")
        val folderQuery = drive.files().list()
            .setQ("mimeType='application/vnd.google-apps.folder' and name='German Learning' and trashed = false")
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        val folderId = if (folderQuery.files.isEmpty()) {
            emit("Creating German Learning folder...")
            val folderMetadata = DriveFile()
                .setName("German Learning")
                .setMimeType("application/vnd.google-apps.folder")
            
            drive.files().create(folderMetadata).execute().id
        } else {
            emit("Found existing German Learning folder")
            folderQuery.files.first().id
        }

        // Sync database
        emit("Syncing database...")
        val dbFile = java.io.File(context.getDatabasePath(database.openHelper.databaseName).path)
        syncFile(drive, folderId, dbFile, "german_learning.db")

        // Sync external storage directory
        if (appDir.exists()) {
            emit("Syncing external storage files...")
            syncDirectory(drive, folderId, appDir)
        } else {
            emit("No external storage files to sync")
        }

        emit("Sync completed successfully")
    }.flowOn(Dispatchers.IO)

    private suspend fun syncDirectory(drive: Drive, parentFolderId: String, directory: java.io.File) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                // Create or find corresponding folder in Drive
                val folderQuery = drive.files().list()
                    .setQ("mimeType='application/vnd.google-apps.folder' and name='${file.name}' and '${parentFolderId}' in parents and trashed = false")
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .execute()

                val folderId = if (folderQuery.files.isEmpty()) {
                    val folderMetadata = DriveFile()
                        .setName(file.name)
                        .setMimeType("application/vnd.google-apps.folder")
                        .setParents(listOf(parentFolderId))
                    
                    drive.files().create(folderMetadata).execute().id
                } else {
                    folderQuery.files.first().id
                }

                // Recursively sync subdirectory
                syncDirectory(drive, folderId, file)
            } else {
                // Sync file
                syncFile(drive, parentFolderId, file, file.name)
            }
        }
    }

    private suspend fun syncFile(drive: Drive, folderId: String, localFile: java.io.File, fileName: String) {
        if (!localFile.exists()) {
            Log.d(TAG, "Local file $fileName does not exist, skipping")
            return
        }

        Log.d(TAG, "Starting sync for file: $fileName")
        
        // Use exact file name for search
        val query = drive.files().list()
            .setQ("name = '$fileName' and '$folderId' in parents and trashed = false")
            .setSpaces("drive")
            .setFields("files(id, name, modifiedTime)")
            .setOrderBy("modifiedTime desc")
            .execute()

        Log.d(TAG, "Found ${query.files.size} matching files in Drive for $fileName")
        
        val localLastModified = localFile.lastModified()
        val localDate = Date(localLastModified)

        if (query.files.isEmpty()) {
            Log.d(TAG, "Creating initial backup of $fileName")
            uploadToDrive(drive, null, localFile, folderId)
        } else {
            // Get the most recently modified file from Drive
            val driveFile = query.files.first()
            val driveLastModified = driveFile.modifiedTime?.value ?: 0L
            val driveDate = if (driveLastModified > 0) Date(driveLastModified) else Date(0)

            Log.d(TAG, "File comparison for $fileName:")
            Log.d(TAG, "Local file path: ${localFile.absolutePath}")
            Log.d(TAG, "Local last modified: $localDate ($localLastModified)")
            Log.d(TAG, "Drive last modified: $driveDate ($driveLastModified)")
            Log.d(TAG, "Drive file ID: ${driveFile.id}")

            if (localLastModified > driveLastModified) {
                Log.d(TAG, "Local version of $fileName is newer, uploading to Drive")
                uploadToDrive(drive, driveFile.id, localFile, folderId)
            } else if (localLastModified < driveLastModified) {
                Log.d(TAG, "Drive version of $fileName is newer, downloading")
                downloadFromDrive(drive, driveFile.id, localFile)
                // Set the local file's timestamp to match Drive's
                localFile.setLastModified(driveLastModified)
            } else {
                Log.d(TAG, "File $fileName is already in sync")
            }

            // Clean up any duplicate files if they exist (keeping only the most recent)
            if (query.files.size > 1) {
                Log.d(TAG, "Found ${query.files.size - 1} duplicate files, cleaning up...")
                query.files.drop(1).forEach { duplicateFile ->
                    drive.files().delete(duplicateFile.id).execute()
                    Log.d(TAG, "Deleted duplicate file with ID: ${duplicateFile.id}")
                }
            }
        }
    }

    private suspend fun uploadToDrive(drive: Drive, fileId: String?, sourceFile: java.io.File, folderId: String) {
        val fileName = sourceFile.name
        Log.d(TAG, "Starting upload for $fileName (fileId: ${fileId ?: "new file"})")

        val fileMetadata = DriveFile()
            .setName(fileName)
            .setModifiedTime(DateTime(sourceFile.lastModified()))

        val mediaContent = FileContent("application/octet-stream", sourceFile)

        if (fileId == null) {
            // For new files, we can set the parent directly
            fileMetadata.setParents(listOf(folderId))
            Log.d(TAG, "Creating new file in Drive: $fileName")
            val file = drive.files().create(fileMetadata, mediaContent)
                .setFields("id, modifiedTime")
                .execute()
            Log.d(TAG, "File created successfully with ID: ${file.id}")
        } else {
            // For existing files, update content and handle parents separately
            Log.d(TAG, "Updating existing file in Drive: $fileName")
            
            // First, update the file content and metadata (except parents)
            val updatedFile = drive.files().update(fileId, fileMetadata, mediaContent)
                .setFields("id, modifiedTime")
                .execute()
            
            // Then, ensure the file is in the correct folder
            val file = drive.files().get(fileId)
                .setFields("parents")
                .execute()
            
            if (!file.parents.contains(folderId)) {
                val previousParents = file.parents.joinToString(",")
                drive.files().update(fileId, null)
                    .setAddParents(folderId)
                    .setRemoveParents(previousParents)
                    .setFields("id, parents")
                    .execute()
            }
            
            Log.d(TAG, "File updated successfully. New modified time: ${updatedFile.modifiedTime?.value}")
        }
    }

    private suspend fun downloadFromDrive(drive: Drive, fileId: String, targetFile: java.io.File) {
        try {
            drive.files().get(fileId).executeMediaAndDownloadTo(FileOutputStream(targetFile))
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading to ${targetFile.name}", e)
            throw e
        }
    }
}
