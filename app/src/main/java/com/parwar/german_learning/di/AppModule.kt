package com.parwar.german_learning.di

import android.content.Context
import com.parwar.german_learning.data.AppDatabase
import com.parwar.german_learning.data.dao.DialogDao
import com.parwar.german_learning.data.dao.FlashCardDao
import com.parwar.german_learning.data.dao.SavedChatDao
import com.parwar.german_learning.data.dao.StudySessionDao
import com.parwar.german_learning.data.SavedChatDatabase
import com.parwar.german_learning.media.MediaManager
import com.parwar.german_learning.utils.PreferencesManager
import com.parwar.german_learning.utils.TextToSpeechManager
import com.parwar.german_learning.utils.TranslationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideFlashCardDao(database: AppDatabase): FlashCardDao {
        return database.flashCardDao()
    }

    @Provides
    @Singleton
    fun provideStudySessionDao(database: AppDatabase): StudySessionDao {
        return database.studySessionDao()
    }

    @Provides
    @Singleton
    fun provideDialogDao(database: AppDatabase): DialogDao {
        return database.dialogDao()
    }

    @Provides
    @Singleton
    fun provideSavedChatDatabase(@ApplicationContext context: Context): SavedChatDatabase {
        return SavedChatDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideSavedChatDao(database: SavedChatDatabase): SavedChatDao {
        return database.savedChatDao()
    }

    @Provides
    @Singleton
    fun provideTextToSpeechManager(@ApplicationContext context: Context): TextToSpeechManager {
        return TextToSpeechManager(context)
    }

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideMediaManager(
        @ApplicationContext context: Context,
        textToSpeechManager: TextToSpeechManager
    ): MediaManager {
        return MediaManager(context, textToSpeechManager)
    }

    @Provides
    @Singleton
    fun provideTranslationManager(@ApplicationContext context: Context): TranslationManager {
        return TranslationManager(context)
    }
}
