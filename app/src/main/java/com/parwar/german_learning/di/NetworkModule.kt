package com.parwar.german_learning.di

import com.parwar.german_learning.network.OpenRouterService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideOpenRouterService(): OpenRouterService {
        return OpenRouterService()
    }
}
